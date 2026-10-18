/** Must match RcInput in rc-player-protocol. No new wire opcode or property type. */
export const Input = Object.freeze({ TEXT: 'rc:input/text:v1', CHOICE: 'rc:input/choice:v1',
    TOGGLE: 'rc:input/toggle:v1', NUMBER: 'rc:input/number:v1', RANGE: 'rc:input/range:v1',
    VALUE: 1, VALUE_RETURN: 2, KEY: 3, LABEL: 4, MULTILINE: 5, OPTIONS: 6, SUBMIT: 7, MIN: 8, MAX: 9, STEP: 10, ENABLED: 11, SUBMIT_LABEL: 12 });

export type InputKind = 'text' | 'choice' | 'toggle' | 'number' | 'range';
export const inputConfigs: readonly string[] = [Input.TEXT, Input.CHOICE, Input.TOGGLE, Input.NUMBER, Input.RANGE];
export function inputValueIsValid(config: string, value: string, options: string[] = [], minimum?: number, maximum?: number, step?: number): boolean {
    if (config === Input.TEXT) return true;
    if (config === Input.CHOICE) return options.includes(value);
    if (config === Input.TOGGLE) return value === 'true' || value === 'false';
    if (config !== Input.NUMBER && config !== Input.RANGE) return false;
    if ([minimum,maximum,step].some(v=>v !== undefined && !Number.isFinite(v)) ||
        (minimum !== undefined && maximum !== undefined && minimum > maximum) || (step !== undefined && step <= 0)) return false;
    if (config === Input.RANGE && (minimum === undefined || maximum === undefined || step === undefined || minimum >= maximum || (maximum-minimum)/step > 10000)) return false;
    if (config === Input.RANGE) {
        const count=(maximum!-minimum!)/step!;
        if (Math.abs(count-Math.round(count)) > 1e-7*Math.max(1,Math.abs(count))) return false;
    }
    if (value.trim() !== value) return false;
    if (!/^-?(0|[1-9][0-9]*)(\.[0-9]+)?([eE][+-]?[0-9]+)?$/.test(value)) return false;
    const number=Number(value);
    if (!Number.isFinite(number) || minimum !== undefined && number < minimum || maximum !== undefined && number > maximum) return false;
    const units=(number-(minimum ?? 0))/(step ?? 1);
    return step === undefined || Number.isFinite(units) && Math.abs(units-Math.round(units)) <= 1e-7*Math.max(1,Math.abs(units));
}
