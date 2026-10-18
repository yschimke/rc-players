import { WireBuffer } from '../core/WireBuffer';
import { Header } from '../core/operations/Header';
import { Input, inputValueIsValid } from './InputContract';
import type { InputKind } from './InputContract';

export interface InputField {
    key: string; label: string; value?: string; options?: string[]; multiline?: boolean; submit?: string; kind?: InputKind; minimum?: number; maximum?: number; step?: number; enabled?: boolean; submitLabel?: string;
}
/** Same LAYOUT_CUSTOM stream as rcInputDocument; no HTML control is authored in the card host. */
export function inputDocument(fields: InputField[], width = 360): ArrayBuffer {
    if (!fields.length || new Set(fields.map(f => f.key)).size !== fields.length ||
        !Number.isInteger(width) || width <= 0 || fields.some(f => !f.key.trim() || !f.label.trim() || f.options?.some(o => !o || o.includes('\n')))) throw new Error('Invalid input fields');
    const configs=fields.map(f=> f.kind ? ({text:Input.TEXT,choice:Input.CHOICE,toggle:Input.TOGGLE,number:Input.NUMBER,range:Input.RANGE})[f.kind] : f.options?.length ? Input.CHOICE : Input.TEXT);
    fields.forEach((f,i)=>{
        if (!configs[i] || f.multiline && configs[i] !== Input.TEXT ||
            (configs[i] === Input.CHOICE ? !f.options?.length : !!f.options?.length) ||
            (f.options && new Set(f.options).size !== f.options.length) ||
            ![Input.NUMBER,Input.RANGE].some(kind=>kind === configs[i]) && [f.minimum,f.maximum,f.step].some(v=>v !== undefined) ||
            [f.minimum,f.maximum,f.step].some(v=>v !== undefined && !Number.isFinite(v)) ||
            f.minimum !== undefined && f.maximum !== undefined && f.minimum > f.maximum || f.step !== undefined && f.step <= 0 ||
            !!f.submit && f.submitLabel !== undefined && !f.submitLabel.trim() ||
            !(!f.value && (configs[i] === Input.CHOICE || configs[i] === Input.NUMBER)) && !inputValueIsValid(configs[i],f.value ?? '',f.options,f.minimum,f.maximum,f.step) ||
            configs[i] === Input.RANGE && !inputValueIsValid(configs[i],String(f.maximum),f.options,f.minimum,f.maximum,f.step)) throw new Error('Invalid input field');
    });
    const b = new WireBuffer();
    const op = (opcode: number, ...ints: number[]) => { b.start(opcode); ints.forEach(i => b.writeInt(i)); };
    const dim = (opcode: number, type: number, value: number) => { op(opcode, type); b.writeFloat(value); };
    const height = (f: InputField) => f.multiline ? 136 : f.submit || f.kind === 'range' ? 112 : 80;
    // Modern header: width + height integer properties, density defaults to one.
    op(0, 0x048c0001, 2, 0, 3);
    b.writeShort(Header.DOC_PROFILES); b.writeShort(4); b.writeInt(0x201);
    b.writeShort(5); b.writeShort(4); b.writeInt(width);
    b.writeShort(6); b.writeShort(4); b.writeInt(fields.reduce((sum, f) => sum + height(f) + 4, 0));
    fields.forEach((f, i) => {
        const texts = [configs[i], f.value ?? '', f.key, f.label, (f.options ?? []).join('\n'), f.submit ?? '', f.minimum === undefined ? '' : String(f.minimum), f.maximum === undefined ? '' : String(f.maximum), f.step === undefined ? '' : String(f.step), f.submitLabel ?? 'Send'];
        texts.forEach((text, j) => { op(102, 100 + i * 16 + j); b.writeUTF8(text); });
    });
    op(200, 1); op(204, 3, 0, 1, 4); b.writeFloat(4);
    dim(16, 1, 1); dim(67, 2, 0); op(201, 4);
    fields.forEach((f, i) => {
        const base = 100 + i * 16;
        op(93, 10 + i, 0, base, 12);
        for (const [type, dataType, value] of [[1,2,base+1], [2,4,base+1], [3,2,base+2], [4,2,base+3], [5,0,f.multiline?1:0], [6,2,base+4], [7,2,base+5], [8,2,base+6], [9,2,base+7], [10,2,base+8], [11,0,f.enabled === false ? 0 : 1], [12,2,base+9]]) {
            b.writeShort(type); b.writeShort(dataType); b.writeInt(value);
        }
        dim(16, 1, 1); dim(67, 0, height(f)); op(214);
    });
    for (let i = 0; i < 3; i++) op(214);
    return b.getBuffer().slice(0, b.getSize()).buffer;
}
