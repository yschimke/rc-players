#!/usr/bin/env node
/** Joins same-sized lane PNGs into a compact left-to-right evidence strip. */
import fs from "node:fs";
import path from "node:path";
import { PNG } from "pngjs";

const [output, ...inputs] = process.argv.slice(2);
if (!output || inputs.length < 2) {
  console.error("usage: node rc-compose-lanes.mjs <output.png> <input.png> <input.png>...");
  process.exit(2);
}

const images = inputs.map((input) => PNG.sync.read(fs.readFileSync(input)));
const [{ width, height }] = images;
if (images.some((image) => image.width !== width || image.height !== height)) {
  console.error("error: every input PNG must have the same dimensions");
  process.exit(1);
}

const gap = 4;
const result = new PNG({
  width: width * images.length + gap * (images.length - 1),
  height,
  colorType: 6,
});
result.data.fill(0xff);
images.forEach((source, imageIndex) => {
  const outputX = imageIndex * (width + gap);
  for (let y = 0; y < height; y += 1) {
    const sourceStart = y * width * 4;
    const targetStart = (y * result.width + outputX) * 4;
    source.data.copy(result.data, targetStart, sourceStart, sourceStart + width * 4);
  }
});

fs.mkdirSync(path.dirname(output), { recursive: true });
fs.writeFileSync(output, PNG.sync.write(result));
