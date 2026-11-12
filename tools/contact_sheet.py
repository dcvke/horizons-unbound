"""Compare scripted game captures without changing their source files.

Requires Pillow. Example:
    py tools/contact_sheet.py output/baseline/screenshots --output output/baseline.png
    py tools/contact_sheet.py fabric/run-test/screenshots --reference output/baseline/screenshots --output output/comparison.png
"""

import argparse
import math
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont, ImageOps


def positive_int(value):
    number = int(value)
    if number < 1:
        raise argparse.ArgumentTypeError("must be a positive integer")
    return number


def captures(paths):
    found = {}
    for path in paths:
        if not path.exists():
            raise ValueError(f"Input does not exist: {path}")
        candidates = sorted(path.rglob("*.png")) if path.is_dir() else [path]
        for candidate in candidates:
            key = candidate.relative_to(path).as_posix() if path.is_dir() else candidate.name
            if key in found:
                raise ValueError(f"Duplicate capture name: {key}")
            found[key] = candidate
    return found


def thumbnail(path, size):
    with Image.open(path) as source:
        return ImageOps.pad(source.convert("RGB"), size, color="#101114")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("captures", nargs="+", type=Path)
    parser.add_argument("--reference", type=Path, help="Match captures by relative filename, with the reference on the left")
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--columns", type=positive_int, default=3, help="Captures or comparison pairs per row")
    parser.add_argument("--width", type=positive_int, default=384, help="Width of each thumbnail")
    parser.add_argument("--height", type=positive_int, default=216, help="Height of each thumbnail")
    args = parser.parse_args()

    try:
        current = captures(args.captures)
        reference = captures([args.reference]) if args.reference is not None else {}
        names = sorted(current.keys() | reference.keys())
        if not names:
            raise ValueError("No PNG captures found")
        input_files = {path.resolve() for path in [*current.values(), *reference.values()]}
        if args.output.resolve() in input_files:
            raise ValueError("Output must not overwrite an input capture")
        paired = args.reference is not None
        sides = [("reference", reference), ("current", current)] if paired else [("", current)]
        columns = min(args.columns, len(names))
        gap, label_height = 12, 36
        cell_width = args.width * len(sides) + gap * (len(sides) - 1)
        cell_height = args.height + label_height
        canvas = Image.new("RGB", (columns * (cell_width + gap) + gap,
                                  math.ceil(len(names) / columns) * (cell_height + gap) + gap), "#202228")
        draw = ImageDraw.Draw(canvas)
        try:
            font = ImageFont.load_default(size=13)
        except TypeError:
            font = ImageFont.load_default()
        missing = 0
        for index, name in enumerate(names):
            x = gap + index % columns * (cell_width + gap)
            y = gap + index // columns * (cell_height + gap)
            for side, (label, files) in enumerate(sides):
                left = x + side * (args.width + gap)
                path = files.get(name)
                if path is not None:
                    canvas.paste(thumbnail(path, (args.width, args.height)), (left, y))
                else:
                    missing += 1
                    draw.rectangle((left, y, left + args.width - 1, y + args.height - 1), fill="#481e28")
                    draw.text((left + 8, y + 8), "MISSING CAPTURE", font=font, fill="white")
                caption = f"{label}: {name}" if label else name
                while caption and draw.textlength(caption, font=font) > args.width - 8:
                    caption = caption[:-1]
                draw.text((left + 4, y + args.height + 8), caption, font=font, fill="white")
        args.output.parent.mkdir(parents=True, exist_ok=True)
        canvas.save(args.output)
        print(f"Saved {args.output}: {len(names)} captures, {missing} missing sides")
    except (OSError, ValueError) as error:
        parser.exit(1, f"error: {error}\n")


if __name__ == "__main__":
    main()
