#!/usr/bin/env python3
# -*- coding: utf-8 -*-


import csv
import re
import sys

from argparse import ArgumentParser


# Matching questions score no points...
TEST_QUESTION_RE = re.compile(r"^\s*(?:(?:image|sound)(?:\[[^\s+]+\])?:\s*[^\s]+\s+)*(?:test|warmup)(?:\s+question)?:\s+", re.I)  # noqa: E501

NUM_ROUNDS = 5  # ...3 or 5 are sensible choices, for a short or regular quiz.

NUM_ROUND_QUESTIONS = 11  # ...including the test/warmup question.
NUM_FINAL_QUESTIONS = 15  # ...usually doesn't have a test question.

MIN_TIEBREAKER_QUESTIONS = 3 * NUM_ROUNDS  # ...more is better.


def parse_args():
    """Parse and enforce command-line arguments."""

    # Disable the automatic "-h/--help" argument to customize its message...
    parser = ArgumentParser(description="Convert questions in CSV to EDN and generate a configuration.", add_help=False)

    parser.add_argument("filename", help="Input file in CSV format.")

    parser.add_argument("-h", "--help", action="help", help="Show the available options and exit.")
    parser.add_argument("-v", "--verbose", action="store_true", help="Produce extra output for debugging purposes.")

    return parser.parse_args()


def main():
    args = parse_args()
    questions = []

    with open(args.filename, "r") as f:
        for question in csv.reader(f, delimiter=",", quotechar="\""):
            questions.append(question if f.encoding else [e.decode("utf-8") for e in question])

    if questions:  # ...drop the header.
        del questions[0]

    min_questions = ((NUM_ROUNDS - 1) * NUM_ROUND_QUESTIONS + NUM_FINAL_QUESTIONS + MIN_TIEBREAKER_QUESTIONS)

    if len(questions) < min_questions:
        print("ERROR: Not enough questions for a valid configuration (minimum: %d)." % min_questions, file=sys.stderr)
        sys.exit(1)

    # Generate the questions file (what will be "questions.edn" in the game engine)...
    with open(re.sub(r"\.csv$", ".edn", args.filename, flags=re.I), "w") as f:
        f.write("[\n")

        for i, question in enumerate(questions, start=0):
            text = question[0].replace("\"", "&quot;")  # ...HTML is allowed here.
            options = "\" \"".join([e.replace("\"", "'").replace("\\", "\\\\") for e in question[1:5]])
            trivia = question[5].replace("\"", "&quot;") if len(question) > 5 else ""
            score = 0 if TEST_QUESTION_RE.match(text) else 1

            out = ("  #pixelsquiz.types.Question{:id %d, "
                                                ":kind :multi, "
                                                ":score %d, "
                                                ":text \"%s\", "
                                                ":options [\"%s\"], "
                                                ":trivia \"%s\"}\n" % (i, score, text, options, trivia))

            f.write(out if f.encoding else out.encode("utf-8"))

        f.write("]\n")

    # Generate the round configuration (what will be "round-config.edn" in the game engine)...
    with open(re.sub(r"\.csv$", ".config.edn", args.filename, flags=re.I), "w") as f:
        f.write("{\n")
        f.write("  :rounds [\n")

        for n in range(NUM_ROUNDS):
            num_questions = NUM_FINAL_QUESTIONS if n == (NUM_ROUNDS - 1) else NUM_ROUND_QUESTIONS

            question_indexes = range(n * num_questions, (n * num_questions) + num_questions)
            question_indexes = " ".join(str(i) for i in question_indexes)

            f.write("    #pixelsquiz.types.Round{:number %d, "
                                                ":questions [%s], "
                                                ":scores [0 0 0 0]}\n" % (n + 1, question_indexes))

        f.write("  ]\n")

        tiebreaker_indexes = range((NUM_ROUNDS - 1) * NUM_ROUND_QUESTIONS + NUM_FINAL_QUESTIONS, len(questions))
        tiebreaker_indexes = " ".join(str(i) for i in tiebreaker_indexes)
        f.write("  :tiebreaker-pool [%s]\n" % tiebreaker_indexes)

        f.write("}\n")


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        pass


# vim: set expandtab ts=4 sw=4:
