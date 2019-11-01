# The Amazing Quizshow!

This is the game engine for [The Amazing Quizshow!](https://quiz.pixels.camp/), as used live at [Pixels Camp](https://pixels.camp/) since 2016.

![Main Screen](https://github.com/PixelsCamp/pixelscamp-quiz-stage/raw/master/extras/mainscreen.png)

## Caveat Emptor

This application was originally written to be used by a **single person**, **once a year**. Expect quirky UX and some roughness around the edges. Having said this, it tries to prevent the quizmaster from making mistakes and should be solid under use. It also provides enough tools (see the embedded REPL) to get out of sticky situations on-stage.

## Running The Engine

It's a Clojure application so you'll need:

* [Java](http://jdk.java.net/) (OpenJDK is recommended)
* [Leiningen](https://leiningen.org/)

It was last known to work with OpenJDK 13 on macOS 10.13.6.

Once you have these requirements installed, run `lein run` in the project directory. It will download the necessary dependencies and start an HTTP server on port `tcp/3000`.

The engine serves webpages for the quizmaster console, main screen, team screens, Buzz! simulator (for testing), and handles input from the Buzz! controllers. See the checklist at `http://localhost:3000/` for assembly instructions and this [blog post](https://blog.pixels.camp/the-quizshow-stage-setup-def8ddf2dab2) for the overall architecture of the quiz setup.

To get started you'll need, at a minimum:

  * Your laptop, with a free USB port (no network connection required);
  * An external **1080p** (1920x1080) screen/projector to show the main screen (the questions);
  * Wired PlayStation Buzz! controllers (you can skip this and use the Buzz! simulator page during testing).

Buzz! controllers are easy to get on eBay and, besides subtle differences in look from one PlayStation version to the next, should work interchangeably. Wireless controllers haven't been tested, but it looks like they'd work too (although it's debatable if using wireless controllers at a conference would be a good idea).

**Note:** The setup at Pixels Camp also has a dedicated screen for each team (provided by the fine folks at [Hipnose](http://hipnose.com/)). These are completely optional but, if used, must be configured for **1920x1080** resolution. We use Raspberry Pis to drive them, with some ugly scripts (not included). You're free to use something better. :)

![All Screens](https://github.com/PixelsCamp/pixelscamp-quiz-stage/raw/master/extras/screenshot.png)

## Running Your Own Quiz

The [quizmaster checklist](http://localhost:3000/) is written for Pixels Camp (ie. assumes the contents of the "quizmaster kit" and a full setup), read it thoroughly and adapt it for your needs.

There's a `csv-to-questions.py` script in the `tooling` directory to take a `.csv` and produce a questions file and a round configuration file in `.edn` format (which you should place in the project root, with the names `questions.edn` and `round-config.edn`, to be picked up by the engine on start). Look at the included examples to see what bits you'll need to come up with (ie. questions, answer options, and quizmaster notes).

The standard quiz model is comprised of four rounds to select four teams to compete in a fifth round (with more, and harder, questions). This can be adapted for fewer players (eg. two rounds selecting four teams to compete in a third round) by adjusting the constants at the top of the `csv-to-questions.py` script.

## Usage Instructions

The best way to learn how to use the engine is by starting it and following this [flow chart](https://github.com/PixelsCamp/pixelscamp-quiz-stage/raw/master/extras/engine_flow.pdf). After a few minutes, you should start getting the hang of it, and then it will all make sense (hopefully).

If you're serious about using this on stage, make sure to look at the bottom of the `core.clj` file for a few functions you can call from the REPL while the engine is running. These functions allow the quizmaster to adjust scores, discard a question, and generally fix mistakes.

To reset the game to its initial state, stop the engine, delete the `game-state.edn*` files, and start it again.

**Note:** If the engine is stopped and restarted, the previous game state is always loaded from disk. However, previous events won't be re-emitted. If you restart the engine *and* reopen the screens, it will look like the game just started from scratch (it will update on the next state change). So, if you restart the engine on-stage, **keep the browser windows open** and the audience won't notice a thing.
