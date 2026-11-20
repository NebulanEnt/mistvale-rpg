"""Reads a Blockbench file (brannoch.bbmodel) and writes the Java 'extras' of BrannochModel.createLayer().
usage: python bb2java.py brannoch.bbmodel   -> prints the lines to paste between 'PartDefinition root = ...' and 'return LayerDefinition...'"""
import json, math, sys
VANILLA_BONES={'head','body','right_arm','left_arm','right_leg','left_leg'}
VANILLA_CUBES={'head','hat','body','jacket','right_arm','right_sleeve','left_arm','left_sleeve','right_leg','right_pants','left_leg','left_pants'}
def r(v):
    v=round(v+0.0,4)
    if v==0: v=0.0
    return v
def F(v):
    v=r(v); s=repr(v)
    return s+'F'
def run(path):
    d=json.load(open(path,encoding='utf8')); els={e['uuid']:e for e in d['elements']}
    lines=[]; varnames={}
    def pose(g,parent_origin,top):
        o=g['origin']; po=parent_origin
        x=-(o[0]-po[0]); y=-(o[1]-po[1]); z=o[2]-po[2]
        if top: y=-(o[1]-24)*1  # not used for extras
        rot=g.get('rotation',[0,0,0])
        rx,ry,rz=-math.radians(rot[0]),-math.radians(rot[1]),math.radians(rot[2])
        if all(abs(v)<1e-9 for v in (x,y,z,rx,ry,rz)): return 'PartPose.ZERO'
        if abs(rx)<1e-9 and abs(ry)<1e-9 and abs(rz)<1e-9: return f'PartPose.offset({F(x)}, {F(y)}, {F(z)})'
        return f'PartPose.offsetAndRotation({F(x)}, {F(y)}, {F(z)}, {F(rx)}, {F(ry)}, {F(rz)})'
    def cubes_expr(cubes,origin):
        parts=[]
        for c in cubes:
            fx,fy,fz=c['from']; tx,ty,tz=c['to']; w,h,dd=tx-fx,ty-fy,tz-fz
            ox=origin[0]-tx; oy=origin[1]-fy-h; oz=fz-origin[2]
            u,v=c['uv_offset']
            infl=c.get('inflate',0)
            tail=f', new CubeDeformation({F(infl)})' if infl else ''
            parts.append(f'.texOffs({int(u)}, {int(v)}).addBox({F(ox)}, {F(oy)}, {F(oz)}, {F(w)}, {F(h)}, {F(dd)}{tail})')
        return 'CubeListBuilder.create()'+''.join(parts)
    def walk(g,parent_var,parent_origin,vanilla_parent):
        name=g['name']; own=[els[c] for c in g['children'] if isinstance(c,str)]
        subs=[c for c in g['children'] if isinstance(c,dict)]
        if g['name'] in VANILLA_BONES and parent_var=='root':
            var={'head':'head','body':'body','right_arm':'rightArm','left_arm':'leftArm','right_leg':'rightLeg','left_leg':'leftLeg'}[name]
            extra=[c for c in own if c['name'] not in VANILLA_CUBES]
            block=[]
            for c in extra:
                block.append(f'        {var}.addOrReplaceChild("{c["name"]}", {cubes_expr([c],g["origin"])}, PartPose.ZERO);')
            for s in subs: block+=walk(s,var,g['origin'],True)
            if block: return [f'        PartDefinition {var} = root.getChild("{name}");']+block+['']
            return []
        var=''.join(w.capitalize() if i else w for i,w in enumerate(name.split('_')))
        out=[f'        PartDefinition {var} = {parent_var}.addOrReplaceChild("{name}", {cubes_expr(own,g["origin"])}, {pose(g,parent_origin,False)});']
        for s in subs: out+=walk(s,var,g['origin'],False)
        return out
    out=[]
    for g in d['outliner']:
        if isinstance(g,dict): out+=walk(g,'root',[0,24,0],False)
    return '\n'.join(out)
if __name__=='__main__': print(run(sys.argv[1]))
