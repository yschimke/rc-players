import { Operation } from '../Operation';
import type { WireBuffer } from '../WireBuffer';
import type { RemoteContext } from '../RemoteContext';
import { BitmapData } from './DataOperations';

export class ImageAttribute extends Operation {
    static readonly OP_CODE = 171;
    static readonly IMAGE_WIDTH = 0;
    static readonly IMAGE_HEIGHT = 1;

    constructor(
        private readonly mOutputId: number,
        private readonly mImageId: number,
        private readonly mType: number,
        private readonly mArgs: number[],
    ) {
        super();
    }

    write(_buffer: WireBuffer): void { /* stub */ }

    apply(context: RemoteContext): void {
        const bitmap = context.getObject(this.mImageId);
        if (!(bitmap instanceof BitmapData)) return;
        let value: number;
        switch (this.mType) {
            case ImageAttribute.IMAGE_WIDTH: value = bitmap.getWidth(); break;
            case ImageAttribute.IMAGE_HEIGHT: value = bitmap.getHeight(); break;
            default: throw new Error(`Unknown image attribute ${this.mType}`);
        }
        context.loadFloat(this.mOutputId, value);
    }

    deepToString(indent: string): string {
        return `${indent}ImageAttribute(${this.mOutputId}, ${this.mImageId}, ${this.mType}, [${this.mArgs.join(', ')}])`;
    }

    static read(buffer: WireBuffer, operations: Operation[]): void {
        const outputId = buffer.readInt();
        const imageId = buffer.readInt();
        const type = buffer.readShort();
        const count = buffer.readShort() & 0xffff;
        const args = Array.from({ length: count }, () => buffer.readInt());
        operations.push(new ImageAttribute(outputId, imageId, type, args));
    }
}
