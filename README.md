# Guru Cue Search &amp; Recommendations Demo Blenders

Blenders are product filtering subsystem in the Guru Cue Search &amp;
Recommendations REST API. This is a fully functional demo implementation of
a set of filters for recommendations (recommendation blenders) and a set of
filters for searching (search blenders).

Although these are regular Java files, blenders subsystem is not meant to be
compiled into a library. The sources (i.e. the content of the
[src/main/java](src/main/java)) should instead be copied into the
`/etc/GuruCue/blenders` directory on the host computer where the Guru Cue
Search &amp; Recommendations REST API is running. The REST API periodically
scans for changes in the directory and recompiles the blenders in-memory when
there is a change. This is done without any downtime or pause for the REST API.

## Blenders Subsystem Architecture
Names of top-level directories of the source must be partner usernames (the
`username` field of the `partner` database entity). Within each of those
directories should be the `recommenders` and `searchers` directories, each
containing exactly one Java class implementing the
`com.gurucue.recommendations.blender.TopBlender` interface. Those two classes
are entry points to recommendation and search blenders for the respective
partner. The naming and the organization of the rest of the files is up to
the implementer.
The demo blenders implementation should be a reasonable starting point to
implement a custom blenders subsystem for a partner; the entry points are
[src/main/java/demo/recommenders/MainBlender.java](src/main/java/demo/recommenders/MainBlender.java)
and
[src/main/java/demo/searchers/MainBlender.java](src/main/java/demo/searchers/MainBlender.java).
