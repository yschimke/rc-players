import type { CustomComponent } from '../core/CustomComponentHost';
import type { PaintContext } from '../core/PaintContext';
import { CanvasPaintContext } from './CanvasPaintContext';

/** Must match RcInput in rc-player-protocol. No new wire opcode or property type. */
export const Input = Object.freeze({ TEXT: 'rc:input/text:v1', CHOICE: 'rc:input/choice:v1',
    VALUE: 1, VALUE_RETURN: 2, KEY: 3, LABEL: 4, MULTILINE: 5, OPTIONS: 6, SUBMIT: 7 });
export interface InputOptions {
    /** Opt in only for authenticated interactive hosts; false for widgets/read-only/demo hosts. */
    editable: boolean;
    /** Retain per card across document replacement. */
    drafts?: Map<string, string>;
    onChange?: (key: string, value: string) => void;
    onSubmit?: (action: string, values: Record<string, string>) => void;
}
interface Entry {
    root: HTMLDivElement;
    control?: HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement;
    component: CustomComponent;
    config: string;
    writable: boolean;
}

/** Native accessible DOM controls positioned by the player's actual canvas transform. */
export class WebInputComponents {
    private entries = new Map<number, Entry>();
    private seen = new Set<number>();
    private drafts: Map<string, string>;
    constructor(private canvas: HTMLCanvasElement, private options: InputOptions) {
        this.drafts = options.drafts ?? new Map();
    }
    beginFrame(): void { this.seen.clear(); }
    endFrame(): void {
        for (const [id, entry] of this.entries) if (!this.seen.has(id)) {
            entry.root.remove(); this.entries.delete(id);
        }
    }
    dispose(): void { for (const entry of this.entries.values()) entry.root.remove(); this.entries.clear(); }
    draw(id: number, config: string, pc: PaintContext, w: number, h: number, component?: CustomComponent): boolean {
        if (config !== Input.TEXT && config !== Input.CHOICE) return false;
        if (!component || !(pc instanceof CanvasPaintContext)) return false;
        this.seen.add(id);
        const key = component.text(Input.KEY);
        const writable = this.options.editable && !!key.trim() && component.canReturnText(Input.VALUE_RETURN);
        let entry = this.entries.get(id);
        if (entry && (entry.config !== config || entry.writable !== writable || entry.component.text(Input.KEY) !== key)) {
            entry.root.remove(); this.entries.delete(id); entry = undefined;
        }
        if (!entry) {
            const root = document.createElement('div');
            root.style.cssText = 'position:absolute;transform-origin:0 0;box-sizing:border-box;overflow:auto;font:14px system-ui;background:var(--card-background-color,white);color:var(--primary-text-color,#202124);padding:6px;';
            // Stop editing gestures from also activating canvas actions.
            for (const event of ['pointerdown', 'click', 'keydown']) root.addEventListener(event, e => e.stopPropagation());
            entry = { root, component, config, writable };
            this.entries.set(id, entry);
            this.canvas.parentNode?.appendChild(root);
            if (writable) {
                const label = document.createElement('label');
                const caption = document.createElement('span');
                caption.textContent = component.text(Input.LABEL);
                caption.style.display = 'block'; label.appendChild(caption);
                const control = config === Input.CHOICE ? document.createElement('select') :
                    component.integer(Input.MULTILINE) === 1 ? document.createElement('textarea') : document.createElement('input');
                if (control instanceof HTMLTextAreaElement) control.rows = 4;
                control.style.cssText = 'font:inherit;width:100%;box-sizing:border-box;min-height:30px;';
                entry.control = control; label.appendChild(control); root.appendChild(label);
                const current = entry;
                control.addEventListener(config === Input.CHOICE ? 'change' : 'input', () => {
                    if (current.component.returnText(Input.VALUE_RETURN, control.value)) {
                        this.drafts.set(key, control.value); this.options.onChange?.(key, control.value);
                    }
                });
                const submit = () => {
                    const action = current.component.text(Input.SUBMIT);
                    if (!action) return;
                    this.options.onSubmit?.(action, { ...Object.fromEntries(this.drafts), [key]: control.value });
                    control.blur();
                };
                control.addEventListener('keydown', event => {
                    const e = event as KeyboardEvent;
                    if (e.key === 'Enter' && !e.isComposing && control instanceof HTMLInputElement) {
                        e.preventDefault(); submit();
                    }
                });
                if (component.text(Input.SUBMIT)) {
                    const button = document.createElement('button'); button.textContent = 'Send';
                    button.addEventListener('click', submit); root.appendChild(button);
                }
            }
        }
        entry.component = component;
        const value = this.drafts.get(key) ?? component.text(Input.VALUE);
        if (entry.control) {
            if (entry.control instanceof HTMLSelectElement) {
                const options = [...new Set(component.text(Input.OPTIONS).split('\n').filter(Boolean))];
                if (Array.from(entry.control.options).map(o => o.value).join('\n') !== options.join('\n')) {
                    entry.control.replaceChildren(...options.map(value => {
                        const option = document.createElement('option'); option.value = value; option.textContent = value; return option;
                    }));
                }
            }
            // Avoid assigning .value when unchanged: preserve caret, selection and IME composition.
            if (entry.control.value !== value) entry.control.value = value;
            if (component.text(Input.VALUE) !== value) component.returnText(Input.VALUE_RETURN, value);
        } else entry.root.textContent = `${component.text(Input.LABEL)}: ${value} · Open in app to edit`;
        const m = pc.getCanvas().getTransform();
        const sx = this.canvas.clientWidth / this.canvas.width, sy = this.canvas.clientHeight / this.canvas.height;
        entry.root.style.left = `${this.canvas.offsetLeft}px`; entry.root.style.top = `${this.canvas.offsetTop}px`;
        entry.root.style.width = `${w}px`; entry.root.style.height = `${h}px`;
        entry.root.style.transform = `matrix(${m.a*sx},${m.b*sy},${m.c*sx},${m.d*sy},${m.e*sx},${m.f*sy})`;
        return true;
    }
}
