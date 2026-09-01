import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { rc2json } from './rc2json';

const fixture = path.resolve(
    process.cwd(),
    '../../rc-player/compose/src/jvmTest/resources/rc-fixtures/ImageBackgroundRemoteButton-454x200.rc',
);
const result = rc2json(new Uint8Array(fs.readFileSync(fixture)));
const operations: any[] = [];
const visit = (value: any): void => {
    if (Array.isArray(value)) {
        value.forEach(visit);
    } else if (value && typeof value === 'object') {
        if (typeof value.opcode === 'number') operations.push(value);
        Object.values(value).forEach(visit);
    }
};
visit(result.operations);

assert.ok(operations.length > 1, 'the complete image-background document must decode');
assert.ok(operations.some((operation) => operation.op === 'ATTRIBUTE_IMAGE'));
assert.ok(
    operations.some(
        (operation) =>
            operation.op === 'PAINT_VALUES' &&
            operation.paintBundle.some((word: number) => (word & 0xffff) === 24),
    ),
    'the decoded corpus must retain its image-texture paint command',
);
assert.ok(
    operations.some(
        (operation) =>
            operation.op === 'PAINT_VALUES' &&
            operation.paintBundle.some((word: number) => (word & 0xffff) === 22),
    ),
    'the decoded corpus must retain its shader-matrix paint command',
);
