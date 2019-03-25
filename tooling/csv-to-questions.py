#!/usr/bin/env python
# -*- coding: utf-8 -*-


from __future__ import print_function

import csv
import re

from argparse import ArgumentParser


# Matching questions score no points...
TEST_QUESTION_RE = re.compile(r"^\s*Test(?:\s+question)?\s*:\s+", re.I)


def parse_args():
    """Parse and enforce command-line arguments."""

    # Disable the automatic "-h/--help" argument to customize its message...
    parser = ArgumentParser(description="Convert questions in CSV to EDN.", add_help=False)

    parser.add_argument("filename", help="Input file in CSV format.")

    parser.add_argument("-h", "--help", action="help", help="Show the available options and exit.")
    parser.add_argument("-v", "--verbose", action="store_true", help="Produce extra output for debugging purposes.")
    parser.add_argument("--log", action="store", metavar="filename", help="Send log messages into the specified file.")

    return parser.parse_args()


def main():
    args = parse_args()
    questions = []

    with open(args.filename, "r") as f:
        for question in csv.reader(f, delimiter=",", quotechar="\""):
            questions.append(question if f.encoding else [e.decode("utf-8") for e in question])

    if questions:  # ...drop the header.
        del questions[0]

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


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        pass


# vim: set expandtab ts=4 sw=4:
