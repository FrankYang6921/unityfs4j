# unityfs4j
A Java library for parsing and extracting Unity AssetBundle files.

```java
var context = new UnityFsContext(Path.of("~/assets/AB/"));
ExtractionManager mgr = ExtractionManager.builder()
    .extractors(Extractor.DEFAULTS)
    .build();
context.findStreams(p -> p.toString().endsWith(".ab"));
mgr.tryExtractAll(context, Path.of("~/extract"));
```
