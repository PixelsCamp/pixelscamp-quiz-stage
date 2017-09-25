#!/usr/bin/env python
# -*- coding: utf-8 -*-


from __future__ import print_function
from __future__ import division
from __future__ import unicode_literals
from __future__ import absolute_import

import sys
import os
import csv
import re

from argparse import ArgumentParser


def parse_args():
    """Parse and enforce command-line arguments."""

    # Disable the automatic "-h/--help" argument to customize its message...
    parser = ArgumentParser(description="Convert questions in CSV to EDN." , add_help=False)

    parser.add_argument("filename", help="Input file in CSV format.")

    parser.add_argument("-h", "--help", action="help", help="Show the available options and exit.")
    parser.add_argument("-v", "--verbose", action="store_true", help="Produce extra output for debugging purposes.")
    parser.add_argument("--log", action="store", metavar="filename", help="Send log messages into the specified file.")

    return parser.parse_args()


def main():
    args = parse_args()

    with open(args.filename, "rb") as f:
        questions = [r for r in csv.reader(f, delimiter=b",", quotechar=b"\"")][1:]

    with open(re.sub(r"\.csv$", ".edn", args.filename, flags=re.I), "wb") as f:
        f.write("[\n")

        question_id = 0

        for question in questions:
            out = ("  #pixelsquiz.types.Question{:id %d, :kind :multi, :score 1, :text \"%s\", :options [\"%s\"]}\n" %
                   (question_id, question[0].decode("utf-8").replace("\"", "'"),
                    "\" \"".join([q.decode("utf-8").replace("\"", "'") for q in question[1:]])))

            f.write(out.encode("utf-8"))

            question_id += 1

        f.write("]")


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        pass


# vim: set expandtab ts=4 sw=4:
