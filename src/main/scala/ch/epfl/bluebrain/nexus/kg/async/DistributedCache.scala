package ch.epfl.bluebrain.nexus.kg.async

import akka.actor.ActorSystem
import akka.cluster.Cluster
import akka.cluster.ddata.LWWRegister.Clock
import akka.cluster.ddata.Replicator._
import akka.cluster.ddata.{DistributedData, LWWRegister, LWWRegisterKey}
import akka.pattern.ask
import akka.util.Timeout
import cats.syntax.show._
import ch.epfl.bluebrain.nexus.admin.client.types.{Account, Project}
import ch.epfl.bluebrain.nexus.kg.RevisionedId
import ch.epfl.bluebrain.nexus.kg.RevisionedId._
import ch.epfl.bluebrain.nexus.kg.RuntimeErr.OperationTimedOut
import ch.epfl.bluebrain.nexus.kg.indexing.View
import ch.epfl.bluebrain.nexus.kg.resolve._
import ch.epfl.bluebrain.nexus.kg.resources._
import ch.epfl.bluebrain.nexus.rdf.Iri.AbsoluteIri
import monix.eval.Task

import scala.concurrent.{ExecutionContext, Future}

/**
  * Contract for the distributed cache that keeps in-memory metadata and content
  * of the resources indexed by the service.
  */
trait DistributedCache[F[_]] {

  /**
    * Looks up the state of the argument account.
    *
    * @param ref the account reference
    * @return Some(account) if there is an account with state and None if there's no information on the account state
    */
  def account(ref: AccountRef): F[Option[Account]]

  /**
    * Looks up the state of the argument account ref.
    *
    * @param ref the project reference
    * @return Some(accountRef) if there is an account reference with state and None if there's no information on the account reference state
    */
  def accountRef(ref: ProjectRef): F[Option[AccountRef]]

  /**
    * Adds an account.
    *
    * @param ref       the account reference
    * @param account   the account to add
    * @param updateRev whether to update an existing account if the provided account has a higher revision than an
    *                  already existing element with the same id
    * @return true if the update was performed or false if the element was already present
    */
  def addAccount(ref: AccountRef, account: Account, updateRev: Boolean): F[Boolean]

  /**
    * Deprecates an account.
    *
    * @param ref the account reference
    * @param rev the account revision
    * @return true if the deprecation was performed or false otherwise
    */
  def deprecateAccount(ref: AccountRef, rev: Long): F[Boolean]

  /**
    * Looks up the state of the argument project.
    *
    * @param ref the project reference
    * @return Some(project) if there is a project with state and None if there's no information on the project state
    */
  def project(ref: ProjectRef): F[Option[Project]]

  /**
    * Looks up the state of the argument project.
    *
    * @param label the project label
    * @return Some(project) if there is a project with state and None if there's no information on the project state
    */
  def project(label: ProjectLabel): F[Option[Project]]

  /**
    * Looks up the state of the argument project ref.
    *
    * @param label the project label
    * @return Some(projectRef) if there is a project reference with state and None if there's no information on the project reference state
    */
  def projectRef(label: ProjectLabel): F[Option[ProjectRef]]

  /**
    * Adds a project.
    *
    * @param ref        the project reference
    * @param accountRef the account reference
    * @param project    the project to add
    * @param updateRev  whether to update an existing project if the provided project has a higher revision than an
    *                   already existing element with the same id
    * @return true if the update was performed or false if the element was already present
    */
  def addProject(ref: ProjectRef, accountRef: AccountRef, project: Project, updateRev: Boolean): F[Boolean]

  /**
    * Deprecates a project.
    *
    * @param ref        the project reference
    * @param accountRef the account reference
    * @param rev        the project revision
    * @return true if the deprecation was performed or false otherwise
    */
  def deprecateProject(ref: ProjectRef, accountRef: AccountRef, rev: Long): F[Boolean]

  /**
    * Looks up the projects belonging to the argument account.
    *
    * @param ref the account reference
    * @return the collection of project references belonging to this account
    */
  def projects(ref: AccountRef): F[Set[ProjectRef]]

  /**
    * Looks up the collection of defined resolvers for the argument project.
    *
    * @param ref the project reference
    * @return the collection of known resolvers configured for the argument project
    */
  def resolvers(ref: ProjectRef): F[Set[Resolver]]

  /**
    * Adds the resolver to the collection of project resolvers.
    *
    * @param ref       the project reference
    * @param resolver  the resolver to add
    * @return true if the update was performed or false if the element was already in the set
    */
  def addResolver(ref: ProjectRef, resolver: Resolver): F[Boolean]

  /**
    * Removes the resolver identified by the argument id from the collection of project resolvers.
    *
    * @param ref     the project reference
    * @param id      the id of the resolver to remove
    * @param rev     revision of the deprecated resolver
    * @return true of the removal was performed or false of the element was not in the set
    */
  def removeResolver(ref: ProjectRef, id: AbsoluteIri, rev: Long): F[Boolean]

  /**
    * Either adds, updates or removes the argument resolver depending on its deprecation state, revision and the current
    * state of the register.
    *
    * @param ref      the project reference
    * @param resolver the resolver
    * @return true if an update has taken place, false otherwise
    */
  def applyResolver(ref: ProjectRef, resolver: Resolver): F[Boolean] =
    if (resolver.deprecated) removeResolver(ref, resolver.id, resolver.rev)
    else addResolver(ref, resolver)

  /**
    * Looks up the collection of defined views for the argument project.
    *
    * @param ref the project reference
    * @return the collection of known views configured for the argument project
    */
  def views(ref: ProjectRef): F[Set[View]]

  /**
    * Looks up the collection of defined views for the argument project.
    *
    * @param label the project label
    * @return the collection of known views configured for the argument project
    */
  def views(label: ProjectLabel): F[Set[View]]

  /**
    * Adds the view to the collection of project views.
    *
    * @param ref       the project reference
    * @param view      the view to add
    * @param updateRev whether to update the view collection if the view provided has a higher revision than an
    *                  already existing element in the collection with the same id
    * @return true if the update was performed or false if the element was already in the set
    */
  def addView(ref: ProjectRef, view: View, updateRev: Boolean): F[Boolean]

  /**
    * Removes the view identified by the argument id from the collection of project views.
    *
    * @param ref the project reference
    * @param id  the id of the view to remove
    * @param rev revision of the deprecated view
    * @return true of the removal was performed or false of the element was not in the set
    */
  def removeView(ref: ProjectRef, id: AbsoluteIri, rev: Long): F[Boolean]

  /**
    * Either adds, updates or removes the argument view depending on its deprecation state, revision and the current
    * state of the register.
    *
    * @param ref     the project reference
    * @param view    the view
    * @return true if an update has taken place, false otherwise
    */
  def applyView(ref: ProjectRef, view: View): F[Boolean] =
    if (view.deprecated) removeView(ref, view.id, view.rev)
    else addView(ref, view, updateRev = true)
}

object DistributedCache {

  private[async] def accountKey(ref: AccountRef): LWWRegisterKey[RevisionedValue[Option[Account]]] =
    LWWRegisterKey("account_state_" + ref.id)

  private[async] def accountRefKey(ref: ProjectRef): LWWRegisterKey[RevisionedValue[Option[AccountRef]]] =
    LWWRegisterKey("account_key_state_" + ref.id)

  private[async] def projectKey(ref: ProjectRef): LWWRegisterKey[RevisionedValue[Option[Project]]] =
    LWWRegisterKey("project_state_" + ref.id)

  private[async] def projectSegmentKey(ref: ProjectLabel): LWWRegisterKey[RevisionedValue[Option[ProjectRef]]] =
    LWWRegisterKey("project_segment_" + ref.show)

  private[async] def accountSegmentInverseKey(ref: AccountRef): LWWRegisterKey[RevisionedValue[Option[String]]] =
    LWWRegisterKey("account_segment_" + ref.id)

  private[async] def accountProjectsKey(ref: AccountRef): LWWRegisterKey[RevisionedValue[Set[ProjectRef]]] =
    LWWRegisterKey("account_projects_" + ref.id)

  private[async] def projectResolversKey(ref: ProjectRef): LWWRegisterKey[RevisionedValue[Set[Resolver]]] =
    LWWRegisterKey("project_resolvers_" + ref.id)

  private[async] def projectViewsKey(ref: ProjectRef): LWWRegisterKey[RevisionedValue[Set[View]]] =
    LWWRegisterKey("project_views_" + ref.id)

  /**
    * Constructs a ''Projects'' instance in a ''Future'' effect type.
    *
    * @param as the underlying actor system
    * @param tm timeout used for the lookup operations
    */
  def future()(implicit as: ActorSystem, tm: Timeout): DistributedCache[Future] = new DistributedCache[Future] {
    private val replicator                    = DistributedData(as).replicator
    private implicit val ec: ExecutionContext = as.dispatcher
    private implicit val node: Cluster        = Cluster(as)

    private implicit def rvClock[A]: Clock[RevisionedValue[A]] = RevisionedValue.revisionedValueClock

    private def update(ref: AccountRef, ac: Account) = {

      def updateAccount() = {
        val empty  = LWWRegister(RevisionedValue[Option[Account]](0L, None))
        val value  = RevisionedValue[Option[Account]](ac.rev, Some(ac))
        val update = Update(accountKey(ref), empty, WriteMajority(tm.duration))(_.withValue(value))
        (replicator ? update).flatMap(handleBooleanUpdate("Timed out while waiting for add project quorum response"))
      }

      def updateAccountLabel() = {
        val empty  = LWWRegister(RevisionedValue[Option[String]](0L, None))
        val value  = RevisionedValue[Option[String]](ac.rev, Some(ac.label))
        val update = Update(accountSegmentInverseKey(ref), empty, WriteMajority(tm.duration))(_.withValue(value))
        (replicator ? update).flatMap(
          handleBooleanUpdate("Timed out while waiting for adding account label quorum response"))
      }

      updateAccount().withFilter(identity).flatMap(_ => updateAccountLabel())
    }

    override def account(ref: AccountRef): Future[Option[Account]] =
      getOrElse(accountKey(ref), none[Account])

    override def accountRef(ref: ProjectRef): Future[Option[AccountRef]] =
      getOrElse(accountRefKey(ref), none[AccountRef])

    private def addAccountRef(ref: ProjectRef, accRef: AccountRef, rev: Long, updateRev: Boolean): Future[Boolean] = {
      accountRef(ref).flatMap {
        case Some(_) => Future.successful(updateRev)
        case _ =>
          val empty  = LWWRegister(RevisionedValue[Option[AccountRef]](0L, None))
          val value  = RevisionedValue[Option[AccountRef]](rev, Some(accRef))
          val update = Update(accountRefKey(ref), empty, WriteMajority(tm.duration))(_.withValue(value))
          (replicator ? update).flatMap(
            handleBooleanUpdate("Timed out while waiting for add account ref quorum response"))
      }
    }

    private def accountSegment(ref: AccountRef): Future[Option[String]] =
      getOrElse(accountSegmentInverseKey(ref), none[String])

    override def addAccount(ref: AccountRef, ac: Account, updateRev: Boolean): Future[Boolean] =
      account(ref).flatMap {
        case None                                   => update(ref, ac)
        case Some(a) if updateRev && ac.rev > a.rev => update(ref, ac)
        case _                                      => Future.successful(false)
      }

    override def deprecateAccount(ref: AccountRef, rev: Long): Future[Boolean] =
      account(ref).flatMap {
        case Some(a) if !a.deprecated && rev > a.rev => update(ref, a.copy(rev = rev, deprecated = true))
        case _                                       => Future.successful(false)
      }

    private def updateProject(ref: ProjectRef, proj: Project): Future[Boolean] = {
      val empty  = LWWRegister(RevisionedValue[Option[Project]](0L, None))
      val value  = RevisionedValue[Option[Project]](proj.rev, Some(proj))
      val update = Update(projectKey(ref), empty, WriteMajority(tm.duration))(_.withValue(value))
      (replicator ? update).flatMap(handleBooleanUpdate("Timed out while waiting for add project quorum response"))
    }

    private def updateProject(ref: ProjectRef,
                              accountRef: AccountRef,
                              proj: Project,
                              updateRev: Boolean): Future[Boolean] = {
      val result = for {
        ra <- updateProject(ref, proj) if ra
        rb <- addProjectToAccount(ref, accountRef, updateRev) if rb
        rc <- addAccountRef(ref, accountRef, proj.rev, updateRev) if rc
        r  <- accountSegment(accountRef)
      } yield r
      result.flatMap {
        case Some(accountLabel) =>
          val empty = LWWRegister(RevisionedValue[Option[ProjectRef]](0L, None))
          val value = RevisionedValue[Option[ProjectRef]](proj.rev, Some(ref))
          val update = Update(projectSegmentKey(ProjectLabel(accountLabel, proj.label)),
                              empty,
                              WriteMajority(tm.duration))(_.withValue(value))
          (replicator ? update).flatMap(handleBooleanUpdate("Timed out while waiting for add project quorum response"))
        case _ => Future.successful(false)
      }
    }

    /**
      * @return true if the project ref is already present so that we can chain the call during an update
      */
    private def addProjectToAccount(ref: ProjectRef, accountRef: AccountRef, updateRev: Boolean): Future[Boolean] = {
      projects(accountRef).flatMap { projects =>
        if (projects.contains(ref)) Future.successful(updateRev)
        else {
          val empty = LWWRegister(RevisionedValue(0L, Set.empty[ProjectRef]))
          val update = Update(accountProjectsKey(accountRef), empty, WriteMajority(tm.duration)) { currentState =>
            val currentRevision = currentState.value.rev
            val currentValue    = currentState.value.value
            currentValue.find(_.id == ref.id) match {
              case Some(r) =>
                currentState.withValue(RevisionedValue(currentRevision + 1, currentValue - r + ref))
              case None =>
                currentState.withValue(RevisionedValue(currentRevision + 1, currentValue + ref))
            }
          }
          (replicator ? update).flatMap(handleBooleanUpdate("Timed out while waiting for add project quorum response"))
        }
      }
    }

    private def removeProjectFromAccount(ref: ProjectRef, accountRef: AccountRef): Future[Boolean] = {
      projects(accountRef).flatMap { projects =>
        if (projects.contains(ref)) {
          val empty = LWWRegister(RevisionedValue(0L, Set.empty[ProjectRef]))
          val update = Update(accountProjectsKey(accountRef), empty, WriteMajority(tm.duration)) { currentState =>
            val currentRevision = currentState.value.rev
            val currentValue    = currentState.value.value
            currentValue.find(_.id == ref.id) match {
              case Some(r) =>
                currentState.withValue(RevisionedValue(currentRevision + 1, currentValue - r))
              case None => currentState
            }
          }
          (replicator ? update).flatMap(
            handleBooleanUpdate("Timed out while waiting for remove project quorum response"))
        } else Future.successful(false)
      }
    }

    override def project(ref: ProjectRef): Future[Option[Project]] =
      getOrElse(projectKey(ref), none[Project])

    override def projectRef(label: ProjectLabel): Future[Option[ProjectRef]] =
      getOrElse(projectSegmentKey(label), none[ProjectRef])

    override def project(label: ProjectLabel): Future[Option[Project]] =
      projectRef(label).flatMap {
        case Some(ref) => project(ref)
        case _         => Future(None)
      }

    override def addProject(ref: ProjectRef,
                            accountRef: AccountRef,
                            proj: Project,
                            updateRev: Boolean): Future[Boolean] =
      project(ref).flatMap {
        case None                                     => updateProject(ref, accountRef, proj, updateRev)
        case Some(p) if updateRev && proj.rev > p.rev => updateProject(ref, accountRef, proj, updateRev)
        case _                                        => Future.successful(false)
      }

    override def deprecateProject(ref: ProjectRef, accountRef: AccountRef, rev: Long): Future[Boolean] =
      project(ref).flatMap {
        case Some(p) if !p.deprecated && rev > p.rev =>
          updateProject(ref, p.copy(rev = rev, deprecated = true))
            .withFilter(identity)
            .flatMap(_ => removeProjectFromAccount(ref, accountRef))
        case _ => Future.successful(false)
      }

    override def projects(ref: AccountRef): Future[Set[ProjectRef]] =
      getOrElse(accountProjectsKey(ref), Set.empty[ProjectRef])

    override def resolvers(ref: ProjectRef): Future[Set[Resolver]] =
      getOrElse(projectResolversKey(ref), Set.empty[Resolver])

    private def getOrElse[T](f: => LWWRegisterKey[RevisionedValue[T]], default: => T): Future[T] =
      (replicator ? Get(f, ReadLocal, None)).map {
        case g @ GetSuccess(LWWRegisterKey(_), _) => g.get(f).value.value
        case NotFound(_, _)                       => default
      }

    override def addResolver(
        ref: ProjectRef,
        resolver: Resolver,
    ): Future[Boolean] = {

      val empty = LWWRegister(RevisionedValue(0L, Set.empty[Resolver]))

      val update = Update(projectResolversKey(ref), empty, WriteMajority(tm.duration))(updateWithIncrement(_, resolver))
      (replicator ? update).flatMap(handleBooleanUpdate("Timed out while waiting for add resolver quorum response"))
    }

    override def removeResolver(ref: ProjectRef, id: AbsoluteIri, rev: Long): Future[Boolean] = {
      val empty  = LWWRegister(RevisionedValue(0L, Set.empty[Resolver]))
      val update = Update(projectResolversKey(ref), empty, WriteMajority(tm.duration))(removeWithIncrement(_, id, rev))
      (replicator ? update).flatMap(handleBooleanUpdate("Timed out while waiting for remove resolver quorum response"))
    }

    private def updateWithIncrement[A: RevisionedId](currentState: LWWRegister[RevisionedValue[Set[A]]],
                                                     value: A): LWWRegister[RevisionedValue[Set[A]]] = {
      val currentRevision = currentState.value.rev
      val current         = currentState.value.value

      current.find(_.id == value.id) match {
        case Some(r) if r.rev >= value.rev => currentState
        case Some(r) =>
          val updated  = current - r + value
          val newValue = RevisionedValue(currentRevision + 1, updated)
          currentState.withValue(newValue)
        case None =>
          val updated  = current + value
          val newValue = RevisionedValue(currentRevision + 1, updated)
          currentState.withValue(newValue)
      }
    }

    private def removeWithIncrement[A: RevisionedId](currentState: LWWRegister[RevisionedValue[Set[A]]],
                                                     id: AbsoluteIri,
                                                     rev: Long): LWWRegister[RevisionedValue[Set[A]]] = {
      val currentRevision = currentState.value.rev
      val current         = currentState.value.value

      current.find(_.id == id) match {
        case Some(r) if r.rev >= rev => currentState
        case Some(r) =>
          val updated  = current - r
          val newValue = RevisionedValue(currentRevision + 1, updated)
          currentState.withValue(newValue)
        case None => currentState
      }
    }

    override def views(ref: ProjectRef): Future[Set[View]] =
      getOrElse(projectViewsKey(ref), Set.empty[View])

    override def views(label: ProjectLabel): Future[Set[View]] =
      projectRef(label).flatMap {
        case Some(ref) => views(ref)
        case _         => Future(Set.empty)
      }

    override def addView(ref: ProjectRef, view: View, updateRev: Boolean): Future[Boolean] = {
      val found = (v: View) =>
        if (updateRev) v.id == view.id && v.rev >= view.rev
        else v.id == view.id

      views(ref).flatMap { viewSet =>
        if (viewSet.exists(found)) Future.successful(false)
        else {
          val empty  = LWWRegister(RevisionedValue(0L, Set.empty[View]))
          val update = Update(projectViewsKey(ref), empty, WriteMajority(tm.duration))(updateWithIncrement(_, view))
          (replicator ? update).flatMap(handleBooleanUpdate("Timed out while waiting for add view quorum response"))
        }
      }
    }

    override def removeView(ref: ProjectRef, id: AbsoluteIri, rev: Long): Future[Boolean] = {
      views(ref).flatMap { viewSet =>
        if (!viewSet.exists(_.id == id)) Future.successful(false)
        else {
          val empty  = LWWRegister(RevisionedValue(0L, Set.empty[View]))
          val update = Update(projectViewsKey(ref), empty, WriteMajority(tm.duration))(removeWithIncrement(_, id, rev))
          (replicator ? update).flatMap(handleBooleanUpdate("Timed out while waiting for remove view quorum response"))
        }
      }
    }

    private def handleBooleanUpdate(timeoutMsg: String): PartialFunction[Any, Future[Boolean]] = {
      case UpdateSuccess(LWWRegisterKey(_), _) =>
        Future.successful(true)
      case UpdateTimeout(LWWRegisterKey(_), _) =>
        Future.failed(OperationTimedOut(timeoutMsg))
    }

    private def none[A]: Option[A] = None
  }

  /**
    * Constructs a ''Projects'' instance in a ''Task'' effect type.
    *
    * @param as the underlying actor system
    * @param tm timeout used for the lookup operations
    */
  def task()(implicit as: ActorSystem, tm: Timeout): DistributedCache[Task] =
    new DistributedCache[Task] {

      private val underlying = future()

      override def views(label: ProjectLabel): Task[Set[View]] =
        Task.deferFuture(underlying.views(label))

      override def projectRef(label: ProjectLabel): Task[Option[ProjectRef]] =
        Task.deferFuture(underlying.projectRef(label))

      override def project(label: ProjectLabel): Task[Option[Project]] =
        Task.deferFuture(underlying.project(label))

      override def account(ref: AccountRef): Task[Option[Account]] =
        Task.deferFuture(underlying.account(ref))

      override def accountRef(ref: ProjectRef): Task[Option[AccountRef]] =
        Task.deferFuture(underlying.accountRef(ref))

      override def addAccount(ref: AccountRef, account: Account, updateRev: Boolean): Task[Boolean] =
        Task.deferFuture(underlying.addAccount(ref, account, updateRev))

      override def deprecateAccount(ref: AccountRef, rev: Long): Task[Boolean] =
        Task.deferFuture(underlying.deprecateAccount(ref, rev))

      override def project(ref: ProjectRef): Task[Option[Project]] =
        Task.deferFuture(underlying.project(ref))

      override def addProject(ref: ProjectRef,
                              accountRef: AccountRef,
                              project: Project,
                              updateRev: Boolean): Task[Boolean] =
        Task.deferFuture(underlying.addProject(ref, accountRef, project, updateRev))

      override def deprecateProject(ref: ProjectRef, accountRef: AccountRef, rev: Long): Task[Boolean] =
        Task.deferFuture(underlying.deprecateProject(ref, accountRef, rev))

      override def projects(ref: AccountRef): Task[Set[ProjectRef]] =
        Task.deferFuture(underlying.projects(ref))

      override def resolvers(ref: ProjectRef): Task[Set[Resolver]] =
        Task.deferFuture(underlying.resolvers(ref))

      override def addResolver(ref: ProjectRef, resolver: Resolver): Task[Boolean] =
        Task.deferFuture(underlying.addResolver(ref, resolver))

      override def removeResolver(ref: ProjectRef, id: AbsoluteIri, rev: Long): Task[Boolean] =
        Task.deferFuture(underlying.removeResolver(ref, id, rev))

      override def views(ref: ProjectRef): Task[Set[View]] =
        Task.deferFuture(underlying.views(ref))

      override def addView(ref: ProjectRef, view: View, updateRev: Boolean): Task[Boolean] =
        Task.deferFuture(underlying.addView(ref, view, updateRev))

      override def removeView(ref: ProjectRef, id: AbsoluteIri, rev: Long): Task[Boolean] =
        Task.deferFuture(underlying.removeView(ref, id, rev))
    }
}
