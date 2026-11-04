package cn.eveonecat.pocket;

final class WindowTransform {
    record Box(int left,int top,int right,int bottom) {}
    static Box map(Box logical,Box physical,Box node){
        int h=logical.bottom-logical.top;
        if(h<=0||physical.bottom<=physical.top)throw new IllegalArgumentException("empty window");
        // HyperOS clips the logical root's right edge to the display but preserves its height.
        double scale=(physical.bottom-physical.top)/(double)h;
        return new Box(physical.left+(int)Math.round((node.left-logical.left)*scale),physical.top+(int)Math.round((node.top-logical.top)*scale),
                physical.left+(int)Math.round((node.right-logical.left)*scale),physical.top+(int)Math.round((node.bottom-logical.top)*scale));
    }
}
