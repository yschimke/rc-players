import type { CustomComponent } from '../core/CustomComponentHost';
import type { PaintContext } from '../core/PaintContext';
import { CanvasPaintContext } from './CanvasPaintContext';
import { mapInputPoint, inverseInputPoint, intersectInputClip } from './InputGeometry';

import { Input, inputConfigs, inputValueIsValid } from './InputContract';
export { Input } from './InputContract';
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
    scope: string;
    shape: string;
    caption?: HTMLSpanElement;
    submitButton?: HTMLButtonElement;
}

/** Native accessible DOM controls positioned by the player's actual canvas transform. */
export class WebInputComponents {
    private entries = new Map<string, Entry>();
    private seen = new Set<string>();
    private scopes: string[] = [];
    private defaults = new Map<string, Map<string, string>>();
    private fields = new Map<string, {component:CustomComponent,config:string}>();
    private hitContext: CanvasRenderingContext2D | null = null;
    private drafts: Map<string, string>;
    constructor(private canvas: HTMLCanvasElement, private options: InputOptions) {
        this.drafts = options.drafts ?? new Map();
    }
    beginFrame(): void { this.seen.clear(); this.defaults.clear(); this.fields.clear(); this.scopes = []; }
    /** Component ids and draft keys are local to a nested document instance. */
    withDocument<T>(identity: string, paint: () => T): T {
        this.scopes.push(identity);
        try { return paint(); } finally { this.scopes.pop(); }
    }
    private storeKey(scope: string, key: string): string { return scope ? `@${scope}:${key}` : key; }
    private values(scope: string): Record<string, string> {
        return Object.fromEntries(Array.from(this.defaults.get(scope) ?? []).filter(([key])=>this.fields.get(this.storeKey(scope,key))?.component.integer(Input.ENABLED,1) === 1).map(([key, value]) => [key, this.drafts.get(this.storeKey(scope, key)) ?? value]));
    }
    private valid(component: CustomComponent, value: string, config: string): boolean {
        if (!component) return false;
        const constraints=[Input.MIN,Input.MAX,Input.STEP].map(p=>component.text(p));
        if (constraints.some(v=>v && !Number.isFinite(Number(v)))) return false;
        return inputValueIsValid(config,value,component.text(Input.OPTIONS).split('\n'),...constraints.map(v=>v ? Number(v) : undefined) as [number?,number?,number?]);
    }
    /** Canvas exports include a deterministic representation beneath native DOM controls. */
    private paintControl(context: CanvasRenderingContext2D, component: CustomComponent, config: string, value: string, writable: boolean, width: number, height: number): void {
        context.save();
        context.beginPath();context.rect(0,0,width,height);context.clip();
        context.fillStyle='#fff';context.fillRect(0,0,width,height);
        context.font='14px system-ui';context.textBaseline='top';context.fillStyle='#202124';
        const label=component.text(Input.LABEL);
        context.fillText(config === Input.RANGE ? `${label}: ${value}` : label,6,6,Math.max(1,width-12));
        if (!writable) {
            context.fillText(value,6,28,Math.max(1,width-12));
            context.fillText(component.integer(Input.ENABLED,1) === 1 ? 'Open in app to edit' : 'Unavailable',6,48,Math.max(1,width-12));
        } else if (config === Input.TOGGLE) {
            context.strokeStyle='#555';context.strokeRect(8,32,18,18);
            if (value === 'true') context.fillText('✓',10,31);
        } else if (config === Input.RANGE) {
            const min=Number(component.text(Input.MIN)),max=Number(component.text(Input.MAX));
            const fraction=Math.max(0,Math.min(1,(Number(value)-min)/(max-min))) || 0;
            context.fillStyle='#bbb';context.fillRect(12,47,Math.max(0,width-24),4);
            context.fillStyle='#1565c0';context.beginPath();context.arc(12+fraction*Math.max(0,width-24),49,7,0,Math.PI*2);context.fill();
        } else {
            const boxHeight=config === Input.TEXT && component.integer(Input.MULTILINE) === 1 ? Math.max(30,height-62) : 30;
            context.strokeStyle='#888';context.strokeRect(6,26,Math.max(0,width-12),boxHeight);
            const lines=value.split('\n');
            for (let i=0;i<lines.length && i*18 < boxHeight-5;i++) context.fillText(lines[i],10,31+i*18,Math.max(1,width-25));
            if (config === Input.CHOICE) context.fillText('▾',Math.max(6,width-22),31);
        }
        if (writable && component.text(Input.SUBMIT)) {
            const title=component.text(Input.SUBMIT_LABEL) || 'Send';
            context.fillStyle='#eee';context.fillRect(6,height-26,Math.min(width-12,Math.max(56,context.measureText(title).width+16)),22);
            context.fillStyle='#202124';context.fillText(title,12,height-23,Math.max(1,width-24));
        }
        context.restore();
    }
    endFrame(): void {
        for (const [id, entry] of this.entries) if (!this.seen.has(id)) {
            entry.root.remove(); this.entries.delete(id);
        }
    }
    dispose(): void { for (const entry of this.entries.values()) entry.root.remove(); this.entries.clear(); }
    draw(id: number, config: string, pc: PaintContext, w: number, h: number, component?: CustomComponent): boolean {
        if (!inputConfigs.includes(config)) return false;
        if (!component || !(pc instanceof CanvasPaintContext)) return false;
        const scope = JSON.stringify(this.scopes);
        const draftScope = this.scopes.length ? scope : '';
        const identity = `${scope}:${id}`;
        this.seen.add(identity);
        const key = component.text(Input.KEY);
        const storeKey = this.storeKey(draftScope, key);
        this.fields.set(storeKey, {component,config});
        let defaults = this.defaults.get(draftScope);
        if (!defaults) { defaults = new Map(); this.defaults.set(draftScope, defaults); }
        defaults.set(key, component.text(Input.VALUE));
        const context = pc.getCanvas();
        const matrix = context.getTransform();
        let polygon = [{x:0,y:0},{x:w,y:0},{x:w,y:h},{x:0,y:h}].map(p => mapInputPoint(matrix, p));
        const clip = pc.getInputClip();
        for (const rectangle of clip.rectangles) polygon = intersectInputClip(polygon, rectangle);
        if (clip.rounded.length && !this.hitContext) this.hitContext = document.createElement('canvas').getContext('2d');
        const roundedCovered = clip.rounded.every(path => polygon.every(p => this.hitContext?.isPointInPath(path, p.x, p.y)));
        if (context.canvas !== this.canvas || clip.unsupported || !roundedCovered) {
            this.entries.get(identity)?.root.remove(); this.entries.delete(identity);
            // Arbitrary path clips/offscreen surfaces stay on the canvas, which applies their
            // exact clip. Never place an interactive overlay outside an unrepresentable region.
            context.save(); context.font = '14px system-ui'; context.fillStyle = '#555';
            context.fillText(`${component.text(Input.LABEL)} · Open in app to edit`, 4, 20, Math.max(1, w-8));
            context.restore();
            return true;
        }
        const constraints = [Input.MIN,Input.MAX,Input.STEP].map(p=>component.text(p));
        const metadataValid = constraints.every(v=>!v || Number.isFinite(Number(v)));
        const writable = metadataValid && component.integer(Input.ENABLED, 1) === 1 && this.options.editable && !!key.trim() && component.canReturnText(Input.VALUE_RETURN);
        const shape = `${config === Input.TEXT && component.integer(Input.MULTILINE) === 1}:${!!component.text(Input.SUBMIT)}`;
        let entry = this.entries.get(identity);
        let restoreFocus = false;
        if (entry && (entry.shape !== shape || entry.config !== config || entry.writable !== writable || entry.component.text(Input.KEY) !== key)) {
            restoreFocus = entry.control === document.activeElement;
            entry.root.remove(); this.entries.delete(identity); entry = undefined;
        }
        if (!entry) {
            const root = document.createElement('div');
            root.style.cssText = 'position:absolute;transform-origin:0 0;box-sizing:border-box;overflow:auto;font:14px system-ui;background:var(--card-background-color,white);color:var(--primary-text-color,#202124);padding:6px;';
            // Stop editing gestures from also activating canvas actions.
            for (const event of ['pointerdown', 'click', 'keydown']) root.addEventListener(event, e => e.stopPropagation());
            entry = { root, component, config, writable, scope: draftScope, shape };
            this.entries.set(identity, entry);
            this.canvas.parentNode?.appendChild(root);
            if (writable) {
                const label = document.createElement('label');
                const caption = document.createElement('span');
                entry.caption = caption;
                caption.textContent = component.text(Input.LABEL);
                caption.style.display = 'block'; label.appendChild(caption);
                const control = config === Input.CHOICE ? document.createElement('select') :
                    config === Input.TEXT && component.integer(Input.MULTILINE) === 1 ? document.createElement('textarea') : document.createElement('input');
                if (control instanceof HTMLTextAreaElement) control.rows = 4;
                if (control instanceof HTMLInputElement) {
                    if (config === Input.TOGGLE) control.type='checkbox';
                    if (config === Input.RANGE) control.type='range';
                    if (config === Input.NUMBER) control.inputMode='decimal';
                }
                control.style.cssText = 'font:inherit;width:100%;box-sizing:border-box;min-height:30px;';
                entry.control = control; label.appendChild(control); root.appendChild(label);
                const current = entry;
                control.addEventListener(config === Input.CHOICE || config === Input.TOGGLE ? 'change' : 'input', () => {
                    const value = config === Input.TOGGLE ? String((control as HTMLInputElement).checked) : control.value;
                    if (current.component.returnText(Input.VALUE_RETURN, value)) {
                        this.drafts.set(storeKey, value); this.options.onChange?.(storeKey, value);
                        if (config === Input.RANGE && current.caption) current.caption.textContent=`${current.component.text(Input.LABEL)}: ${value}`;
                        control.setCustomValidity(this.valid(current.component,value,config) ? "" : "Enter a valid value");
                    }
                });
                const submit = () => {
                    const action = current.component.text(Input.SUBMIT);
                    if (!action) return;
                    const values = this.values(current.scope);
                    if (!Object.entries(values).every(([name,value])=>((field)=>!!field && this.valid(field.component,value,field.config))(this.fields.get(this.storeKey(current.scope,name))))) {
                        for (const entry of this.entries.values()) if (entry.scope === current.scope) entry.control?.reportValidity();
                        return;
                    }
                    this.options.onSubmit?.(action, values);
                    control.blur();
                };
                control.addEventListener('keydown', event => {
                    const e = event as KeyboardEvent;
                    if (e.key === 'Enter' && !e.isComposing && control instanceof HTMLInputElement && config !== Input.TOGGLE && config !== Input.RANGE) {
                        e.preventDefault(); submit();
                    }
                });
                if (component.text(Input.SUBMIT)) {
                    const button = document.createElement('button'); entry.submitButton=button; button.textContent = component.text(Input.SUBMIT_LABEL) || 'Send';
                    button.addEventListener('click', submit); root.appendChild(button);
                }
            }
        }
        entry.component = component;
        if (entry.submitButton) entry.submitButton.textContent=component.text(Input.SUBMIT_LABEL) || 'Send';
        if (entry.caption) entry.caption.textContent = config === Input.RANGE ? `${component.text(Input.LABEL)}: ${this.drafts.get(storeKey) ?? component.text(Input.VALUE)}` : component.text(Input.LABEL);
        const value = this.drafts.get(storeKey) ?? component.text(Input.VALUE);
        if (entry.control) {
            if (entry.control instanceof HTMLSelectElement) {
                const options = [...new Set(component.text(Input.OPTIONS).split('\n').filter(Boolean))];
                if (Array.from(entry.control.options).map(o => o.value).join('\n') !== options.join('\n')) {
                    entry.control.replaceChildren(...options.map(value => {
                        const option = document.createElement('option'); option.value = value; option.textContent = value; return option;
                    }));
                }
            }
            if (entry.control instanceof HTMLInputElement && config === Input.RANGE) {
                entry.control.min=component.text(Input.MIN); entry.control.max=component.text(Input.MAX); entry.control.step=component.text(Input.STEP);
            }
            if (entry.control instanceof HTMLInputElement && config === Input.TOGGLE) entry.control.checked=value === 'true';
            entry.control.setCustomValidity(this.valid(component,value,config) ? "" : "Enter a valid value");
            // Avoid assigning .value when unchanged: preserve caret, selection and IME composition.
            if (entry.control.value !== value) entry.control.value = value;
            if (component.text(Input.VALUE) !== value) component.returnText(Input.VALUE_RETURN, value);
        } else entry.root.textContent = `${component.text(Input.LABEL)}: ${value} · ${component.integer(Input.ENABLED, 1) !== 1 ? "Unavailable" : "Open in app to edit"}`;
        this.paintControl(context,component,config,value,writable,w,h);
        if (restoreFocus) entry.control?.focus();
        const m = matrix;
        entry.root.hidden = !polygon.length || context.globalAlpha <= 0;
        entry.root.style.opacity = String(context.globalAlpha);
        if (entry.root.hidden) entry.control?.blur();
        entry.root.style.clipPath = polygon.length ? `polygon(${polygon.map(p => { const local = inverseInputPoint(m, p); return `${local.x}px ${local.y}px`; }).join(',')})` : 'inset(100%)';
        const sx = this.canvas.clientWidth / this.canvas.width, sy = this.canvas.clientHeight / this.canvas.height;
        entry.root.style.left = `${this.canvas.offsetLeft}px`; entry.root.style.top = `${this.canvas.offsetTop}px`;
        entry.root.style.width = `${w}px`; entry.root.style.height = `${h}px`;
        entry.root.style.transform = `matrix(${m.a*sx},${m.b*sy},${m.c*sx},${m.d*sy},${m.e*sx},${m.f*sy})`;
        return true;
    }
}
