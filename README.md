# The Amazing Quizshow!

This is the game engine for the Pixels Camp quiz show, to be used for the the live event on-stage. It's a Clojure application so you'll need:

* [Java SE](http://www.oracle.com/technetwork/java/javase/downloads/)
* [Leiningen](https://leiningen.org/)

It was last known to work with JDK 9.0.1 on macOS 10.12.6.

Once you have these requirements installed, run `lein run` in the project directory. It will download the necessary dependencies and start a server on port `tcp/3000`.

The server handles both websocket connections from the several components, as well as webpages for the quizmaster console, main screen, etc. See the checklist at http://localhost:3000/static/ for assembly instructions and this [blog post](https://blog.pixels.camp/the-quizshow-stage-setup-def8ddf2dab2) for the overall architecture of the quiz setup.

You should find all the necessary bits and pieces inside the quizmaster bag. The only exception are the stand monitors for each team. These monitors are provided by Hipnose (the Pixels Camp producing company).
