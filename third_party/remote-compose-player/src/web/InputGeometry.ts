export interface InputPoint { x: number; y: number; }
export interface InputMatrix { a: number; b: number; c: number; d: number; e: number; f: number; }
export function mapInputPoint(m: InputMatrix, p: InputPoint): InputPoint {
    return { x: m.a*p.x + m.c*p.y + m.e, y: m.b*p.x + m.d*p.y + m.f };
}
export function inverseInputPoint(m: InputMatrix, p: InputPoint): InputPoint {
    const determinant = m.a*m.d - m.b*m.c;
    return { x: (m.d*(p.x-m.e) - m.c*(p.y-m.f))/determinant,
             y: (-m.b*(p.x-m.e) + m.a*(p.y-m.f))/determinant };
}
/** Exact intersection for transformed rectangle clips, including rotated/reflected ancestors. */
export function intersectInputClip(subject: InputPoint[], clip: InputPoint[]): InputPoint[] {
    const cross = (a: InputPoint, b: InputPoint, p: InputPoint) => (b.x-a.x)*(p.y-a.y)-(b.y-a.y)*(p.x-a.x);
    const area = clip.reduce((sum, p, i) => { const q = clip[(i+1)%clip.length]; return sum+p.x*q.y-q.x*p.y; }, 0);
    if (Math.abs(area) < 1e-7) return [];
    const sign = area >= 0 ? 1 : -1;
    let result = subject;
    for (let i = 0; i < clip.length && result.length; i++) {
        const a = clip[i], b = clip[(i+1)%clip.length], input = result;
        result = [];
        let previous = input[input.length-1];
        let previousSide = sign*cross(a, b, previous);
        for (const point of input) {
            const side = sign*cross(a, b, point);
            if ((side >= -1e-7) !== (previousSide >= -1e-7)) {
                const t = previousSide/(previousSide-side);
                result.push({ x: previous.x+(point.x-previous.x)*t, y: previous.y+(point.y-previous.y)*t });
            }
            if (side >= -1e-7) result.push(point);
            previous = point; previousSide = side;
        }
    }
    return result;
}
