curl -XPUT -H "Content-Type: application/json" "https://nexus.example.com/v0/contexts/myorg/mydom/mycontext/v1.0.0?rev=1"
    -d '{"@context":{"class":{"@id":"sh:class","@type":"@id"},"rootClass":{"@id":"shext:rootClass","@type":"@id"},"path":{"@id":"sh:path","@type":"@id"},"qualifiedValueShape":{"@id":"sh:qualifiedValueShape","@type":"@id","@container":"@list"},"qualifiedValueShapesDisjoint":{"@id":"sh:qualifiedValueShapesDisjoint","@type":"xsd:boolean"},"qualifiedMinCount":{"@id":"sh:qualifiedMinCount","@type":"xsd:integer"},"qualifiedMaxCount":{"@id":"sh:qualifiedMaxCount","@type":"xsd:integer"},"maxCount":{"@id":"sh:maxCount","@type":"xsd:integer"},"minCount":{"@id":"sh:minCount","@type":"xsd:integer"},"minInclusive":"sh:minInclusive","maxInclusive":"sh:maxInclusive","maxExclusive":"sh:maxExclusive","minExclusive":"sh:minExclusive","in":{"@id":"sh:in","@container":"@list"},"imports":{"@id":"owl:imports","@type":"@id","@container":"@set"},"datatype":{"@id":"sh:datatype","@type":"@id"},"description":"sh:description","name":"sh:name","nodeKind":{"@id":"sh:nodeKind","@type":"@id"},"node":{"@id":"sh:node","@type":"@id"},"property":{"@id":"sh:property","@type":"@id","@container":"@set"},"targetClass":{"@id":"sh:targetClass","@type":"@id"},"targetObjectsOf":{"@id":"sh:targetObjectsOf","@type":"@id"},"targetSubjectsOf":{"@id":"sh:targetSubjectsOf","@type":"@id"},"isDefinedBy":{"@id":"http://www.w3.org/2000/01/rdf-schema#isDefinedBy","@type":"@id"},"shapes":{"@reverse":"http://www.w3.org/2000/01/rdf-schema#isDefinedBy","@type":"@id","@container":"@set"},"or":{"@id":"sh:or","@type":"@id","@container":"@list"},"and":{"@id":"sh:and","@type":"@id","@container":"@list"},"xone":{"@id":"sh:xone","@type":"@id","@container":"@list"},"not":{"@id":"sh:not","@type":"@id","@container":"@list"},"lessThan":{"@id":"sh:lessThan","@type":"@id"},"hasValue":"sh:hasValue","owl":"http://www.w3.org/2002/07/owl#","rdf":"http://www.w3.org/1999/02/22-rdf-syntax-ns#","rdfs":"http://www.w3.org/2000/01/rdf-schema#","xsd":"http://www.w3.org/2001/XMLSchema#","sh":"http://www.w3.org/ns/shacl#","shext":"http://www.w3.org/ns/shacl/ext#","schema":"http://schema.org/","prov":"http://www.w3.org/ns/prov#","this":"{{scheme}}://{{host}}/{{prefix}}/schemas/nexus/schemaorg/quantitativevalue/v1.0.0/shapes/"}}'