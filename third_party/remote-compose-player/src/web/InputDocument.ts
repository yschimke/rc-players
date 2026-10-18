import { WireBuffer } from '../core/WireBuffer';
import { Header } from '../core/operations/Header';
import { Input } from './InputComponents';

export interface InputField {
    key: string; label: string; value?: string; options?: string[]; multiline?: boolean; submit?: string;
}
/** Same LAYOUT_CUSTOM stream as rcInputDocument; no HTML control is authored in the card host. */
export function inputDocument(fields: InputField[], width = 360): ArrayBuffer {
    if (!fields.length || new Set(fields.map(f => f.key)).size !== fields.length ||
        fields.some(f => !f.key.trim() || f.options?.some(o => !o || o.includes('\n')))) throw new Error('Invalid input fields');
    const b = new WireBuffer();
    const op = (opcode: number, ...ints: number[]) => { b.start(opcode); ints.forEach(i => b.writeInt(i)); };
    const dim = (opcode: number, type: number, value: number) => { op(opcode, type); b.writeFloat(value); };
    const height = (f: InputField) => f.multiline ? 136 : f.submit ? 112 : 80;
    // Modern header: width + height integer properties, density defaults to one.
    op(0, 0x048c0001, 2, 0, 3);
    b.writeShort(Header.DOC_PROFILES); b.writeShort(4); b.writeInt(0x201);
    b.writeShort(5); b.writeShort(4); b.writeInt(width);
    b.writeShort(6); b.writeShort(4); b.writeInt(fields.reduce((sum, f) => sum + height(f) + 4, 0));
    fields.forEach((f, i) => {
        const texts = [f.options?.length ? Input.CHOICE : Input.TEXT, f.value ?? '', f.key, f.label, (f.options ?? []).join('\n'), f.submit ?? ''];
        texts.forEach((text, j) => { op(102, 100 + i * 10 + j); b.writeUTF8(text); });
    });
    op(200, 1); op(204, 3, 0, 1, 4); b.writeFloat(4);
    dim(16, 1, 1); dim(67, 2, 0); op(201, 4);
    fields.forEach((f, i) => {
        const base = 100 + i * 10;
        op(93, 10 + i, 0, base, 7);
        for (const [type, dataType, value] of [[1,2,base+1], [2,4,base+1], [3,2,base+2], [4,2,base+3], [5,0,f.multiline?1:0], [6,2,base+4], [7,2,base+5]]) {
            b.writeShort(type); b.writeShort(dataType); b.writeInt(value);
        }
        dim(16, 1, 1); dim(67, 0, height(f)); op(214);
    });
    for (let i = 0; i < 3; i++) op(214);
    return b.getBuffer().slice(0, b.getSize()).buffer;
}
