package com.arina.assistant.ui

/** The extra orb designs (AGSL). Every one reads the same inputs: time, energy (mood), level (voice), melt and the four colours. */
internal object OrbDesigns {
    private const val HEAD = """
uniform float2 res;
uniform float t;
uniform float energy;
uniform float level;
uniform float melt;
uniform float3 cCore;
uniform float3 cLight;
uniform float3 cMid;
uniform float3 cDeep;
float hash(float2 p) { return fract(sin(dot(p, float2(12.9898, 78.233))) * 43758.5453); }
float vnoise(float2 p){float2 i=floor(p);float2 f=fract(p);f=f*f*(3.0-2.0*f);
 return mix(mix(hash(i),hash(i+float2(1.0,0.0)),f.x),mix(hash(i+float2(0.0,1.0)),hash(i+float2(1.0,1.0)),f.x),f.y);}
float fbm(float2 p){float v=0.0;float a=0.5;for(int i=0;i<4;i++){v+=a*vnoise(p);p=p*2.03+float2(7.1,3.7);a*=0.5;}return v;}
float3 ramp(float k){float3 c=mix(cDeep,cMid,smoothstep(0.0,0.4,k));c=mix(c,cLight,smoothstep(0.35,0.75,k));return mix(c,cCore,smoothstep(0.78,1.0,k));}
float4 pm(float3 c,float a){a=clamp(a,0.0,1.0);return float4(clamp(c,0.0,1.0)*a,a);}
"""
    private const val TAIL = """
half4 main(float2 fc) {
  float2 p = (fc - 0.5 * res) / (0.5 * min(res.x, res.y));
  float edge = 1.0 - smoothstep(0.84, 0.99, length(p));
  float2 cell = floor(p * 46.0);
  p += (float2(hash(cell), hash(cell + 7.3)) - 0.5) * melt * 1.6;
  float4 o = shade(p);
  o *= pow(1.0 - melt, 1.5) * edge;
  return half4(o);
}
"""

    val MALE1_STORM = HEAD + """
float wrapa(float x){return mod(x+3.14159,6.28318)-3.14159;}
float4 shade(float2 p){
 float a=atan(p.y,p.x);float r=length(p);
 float tf=floor(t*12.0);
 float Rs=0.14*(1.0+0.18*level);float k=r/Rs;
 float sph=1.0-smoothstep(0.92,1.0,k);
 float pl=fbm(p*9.0+float2(t*0.9,-t*0.7));
 float3 sc=mix(cMid,cLight,pl);sc=mix(sc,cCore,exp(-k*k*2.2));
 float halo=exp(-r*r*9.0)*0.50*(0.6+0.4*energy);
 float halo2=exp(-max(r-Rs,0.0)*18.0)*0.7;
 float bolt=0.0;
 for(int i=0;i<12;i++){float fi=float(i);
  float ai=fi*0.5236+hash(float2(fi,tf))*0.45+0.12*sin(t*0.9+fi);
  float j1=(vnoise(float2(r*11.0+fi*7.0,tf*1.7+fi))-0.5)*0.75*r;
  float j2=(vnoise(float2(r*30.0+fi,tf*2.3))-0.5)*0.16*r;
  float ad=wrapa(a-ai)+j1+j2;float d=ad*r;
  float len=0.34+0.55*hash(float2(fi,tf*0.5))*(0.6+0.6*energy)+0.10*level;
  float env=smoothstep(Rs*0.8,Rs*1.15,r)*(1.0-smoothstep(len*0.55,len,r));
  bolt+=(exp(-d*d*14000.0)+0.5*exp(-d*d*800.0))*env;
  float sg=(mod(fi,2.0)<0.5)?1.0:-1.0;
  float rf=len*0.36;
  float adf=wrapa(a-ai-sg*1.1*max(r-rf,0.0))+(vnoise(float2(r*26.0+fi*3.0,tf*2.9))-0.5)*0.2*r;
  float df=adf*r;
  float envf=smoothstep(rf,rf+0.02,r)*(1.0-smoothstep(len*0.55,len*0.9,r));
  bolt+=exp(-df*df*20000.0)*envf*0.75;}
 bolt=min(bolt,1.6);
 float white=pow(min(bolt,1.0),3.0);
 float tot=sph+halo+halo2+bolt;
 float3 cc=(sc*sph+cMid*halo+cLight*halo2+mix(cMid,cLight,0.55)*bolt+cCore*white*0.8)/max(tot,0.001);
 return pm(cc,tot);}
""" + TAIL

    val MALE4_ATOM = HEAD + """
float wrapa(float x){return mod(x+3.14159,6.28318)-3.14159;}
float4 shade(float2 p){
 float r=length(p);
 float3 col=float3(0.0);float sw=0.0;
 for(int i=0;i<3;i++){float fi=float(i);
  float rot=fi*1.0472+0.45;float c=cos(rot);float s=sin(rot);
  float2 q=float2(p.x*c+p.y*s,-p.x*s+p.y*c);
  float A=0.74;float B=0.25+0.015*fi;
  float2 qe=float2(q.x/A,q.y/B);
  float e=(length(qe)-1.0)*B;
  float u=atan(qe.y,qe.x);
  float dir=(mod(fi,2.0)<0.5)?1.0:-1.0;
  float h=t*(0.9+0.45*fi+1.1*energy)*dir+fi*2.1;
  float da=wrapa((h-u)*dir);
  float line=exp(-e*e*3200.0)*0.30;
  float tail=exp(-clamp(da,0.0,3.5)*2.2)*smoothstep(0.0,0.10,da)*(1.0-smoothstep(2.2,3.2,da));
  float tw=exp(-e*e*(2200.0+5200.0*clamp(da,0.0,3.5)));
  float w=line+tail*tw*1.1;
  float2 pe=float2(A*cos(h),B*sin(h));
  float dq=length(q-pe);
  float depth=0.70+0.30*sin(h);
  float hd=exp(-dq*dq*1300.0)*1.8*depth;
  float hh=exp(-dq*dq*160.0)*0.65*depth;
  float3 cc=mix(cMid,cLight,fi/2.0);
  col+=cc*(w+hh)+cCore*hd;sw+=w+hh+hd;}
 float pulse=0.5+0.5*sin(t*2.6);
 float core=exp(-r*r*140.0)*1.8;
 float shell=exp(-(r-0.075-0.012*pulse)*(r-0.075-0.012*pulse)*3025.0)*0.5;
 float halo=exp(-r*r*18.0)*(0.35+0.25*energy+0.25*level);
 col+=cCore*core+cLight*shell+cMid*halo;sw+=core+shell+halo;
 return pm(col/max(sw,0.001),sw);}
""" + TAIL

    val MALE2_TESSERACT = HEAD + """
float3 tv(float i,float c1,float s1,float c2,float s2){
 float x=mod(floor(i),2.0)*2.0-1.0;float y=mod(floor(i/2.0),2.0)*2.0-1.0;
 float z=mod(floor(i/4.0),2.0)*2.0-1.0;float w=mod(floor(i/8.0),2.0)*2.0-1.0;
 float x1=x*c1-w*s1;float w1=x*s1+w*c1;
 float y2=y*c2-z*s2;float z2=y*s2+z*c2;
 float k=1.0/(2.7-w1*0.9);
 float3 P=float3(x1,y2,z2)*k;
 float k2=1.0/(3.6-P.z*0.7);
 return float3(P.x*k2*2.25,P.y*k2*2.25,P.z);}
float4 shade(float2 p){
 float r=length(p);
 float c1=cos(t*0.55);float s1=sin(t*0.55);float c2=cos(t*0.38+0.7);float s2=sin(t*0.38+0.7);
 float acc=0.0;float3 col=float3(0.0);
 for(int i=0;i<16;i++){float fi=float(i);
  float3 A=tv(fi,c1,s1,c2,s2);
  float dv=length(p-A.xy);
  float dp=exp(-dv*dv*2800.0)*(0.5+0.5*smoothstep(-1.0,1.0,A.z));
  col+=mix(cLight,cCore,smoothstep(-0.5,1.0,A.z))*dp;acc+=dp;
  for(int k=0;k<4;k++){
   float pk=exp2(float(k));
   if(mod(floor(fi/pk),2.0)<0.5){
    float3 B=tv(fi+pk,c1,s1,c2,s2);
    float2 ab=B.xy-A.xy;float l2=max(dot(ab,ab),0.0001);
    float h=clamp(dot(p-A.xy,ab)/l2,0.0,1.0);
    float dl=length(p-A.xy-ab*h);
    float zz=0.5*(A.z+B.z);
    float lm=exp(-dl*dl*6500.0)*(0.30+0.70*smoothstep(-1.0,1.0,zz));
    float pulse=exp(-(h-fract(t*0.4+fi*0.13+pk*0.05))*(h-fract(t*0.4+fi*0.13+pk*0.05))*30.0);
    col+=mix(cMid,cLight,smoothstep(-0.6,0.9,zz))*lm*(1.0+0.9*pulse*(0.4+energy));acc+=lm;}}}
 float core=exp(-r*r*70.0)*(0.5+0.4*energy);
 col+=cCore*core;acc+=core*0.6;
 float halo=exp(-r*r*5.0)*0.22;
 float tot=acc+halo;
 return pm((col+cMid*halo)/max(tot,0.001),tot);}
""" + TAIL

    val FEM0_FLOWER = HEAD + """
float4 shade(float2 p){
 float a=atan(p.y,p.x);float r=length(p);
 float breath=1.0+0.045*sin(t*1.1)+0.10*level+0.05*energy;
 float3 col=float3(0.0);float al=0.0;
 for(int i=0;i<4;i++){float fi=float(i);
  float nPet=(i<2)?8.0:6.0;
  float dir=(mod(fi,2.0)<0.5)?1.0:-1.0;
  float rot=fi*0.3927+dir*t*(0.10+0.05*fi)*(0.6+energy);
  float Rl=(0.58-0.115*fi)*breath;
  float cs=abs(cos(nPet*0.5*(a+rot)));
  float shape=0.28+0.72*pow(cs,1.25);
  float rp=Rl*shape;
  float m=1.0-smoothstep(rp-0.010,rp,r);
  float tt=clamp(r/max(rp,0.001),0.0,1.0);
  float3 base=mix(cMid,cLight,fi/3.0);
  float3 cl=mix(base,cCore,0.60*(1.0-tt)*(1.0-tt));
  cl*=0.86+0.14*cs;
  float rim=exp(-(r-rp)*(r-rp)*5200.0);
  col=mix(col,cl,m);al=max(al,m);
  col=mix(col,cCore,rim*0.55*m*(1.0-fi*0.15));}
 float d1=exp(-(r-0.078*breath)*(r-0.078*breath)*4900.0)*(0.35+0.65*pow(0.5+0.5*cos(10.0*a-t*2.0),4.0));
 float cg=exp(-r*r*380.0);
 col=mix(col,cCore,clamp(d1+cg,0.0,1.0));al=max(al,max(d1,cg));
 float glow=exp(-max(r-0.58*breath,0.0)*9.0)*0.38*(0.5+energy);
 float tot=al+glow;
 return pm((col*al+cLight*glow)/max(tot,0.001),tot);}
""" + TAIL

    val MALE3_CRYSTAL = HEAD + """
float4 shade(float2 p){
 float a=atan(p.y,p.x);float r=length(p);
 float sp=t*0.25*(0.6+energy);
 float ar=a+sp;
 float N=8.0;float sec=6.28318/N;
 float lid=floor(ar/sec);float loc=mod(ar,sec)-sec*0.5;
 float R=0.56*(1.0+0.06*level);
 float edgeR=R*cos(sec*0.5)/max(cos(loc),0.3);
 float m=1.0-smoothstep(edgeR-0.012,edgeR,r);
 float zone=r/edgeR;
 float fid=lid+floor(zone*3.0)*3.0;
 float sh=0.5+0.5*sin(t*1.3*(0.5+energy)+hash(float2(fid,2.0))*6.28318);
 float h2=hash(float2(fid,5.0));
 float3 c=mix(cDeep,cMid,0.35+0.5*h2);
 c=mix(c,cLight,sh*0.55+0.20*h2);
 float table=1.0-smoothstep(0.18,0.20,zone);
 c=mix(c,cCore,table*(0.55+0.3*sh));
 float spoke=exp(-abs(loc)*abs(loc)*r*r*5200.0)*smoothstep(0.18,0.30,zone);
 float ring=exp(-(zone-0.38)*(zone-0.38)*900.0)+exp(-(zone-0.68)*(zone-0.68)*900.0);
 float rimc=exp(-(zone-1.0)*(zone-1.0)*500.0);
 c=mix(c,cCore,clamp(spoke*0.55+ring*0.45+rimc*0.8,0.0,1.0));
 float glint=pow(max(0.0,sin(ar*N*0.5+t*1.2)),24.0)*0.8*smoothstep(0.3,0.9,zone);
 c+=cCore*glint;
 float glow=exp(-max(r-edgeR,0.0)*9.0)*0.36*(0.5+energy);
 float tot=m+glow;
 return pm((c*m+cLight*glow)/max(tot,0.001),tot);}
""" + TAIL

    val FEM1_FLUID = HEAD + """
float4 shade(float2 p){
 float a=atan(p.y,p.x);float r=length(p);
 float R=0.56*(1.0+0.10*level);
 float wob=0.07*(0.5+energy)*sin(3.0*a+t*1.3)+0.05*(0.4+energy)*sin(5.0*a-t*1.9)+0.035*sin(2.0*a+t*0.9);
 float edge=R*(1.0+wob);
 float m=1.0-smoothstep(edge-0.012,edge+0.012,r);
 float2 q=p*2.2;q+=float2(fbm(q+t*0.3),fbm(q-t*0.25+3.0))*1.4;
 float k=fbm(q+t*0.15)*1.35+0.3-r/R*0.35;
 float3 c=ramp(clamp(k,0.0,1.0));
 float2 hp=(p-float2(-0.17,-0.22))/float2(0.20,0.11);
 float spec=exp(-dot(hp,hp))*0.75;
 float glow=exp(-max(r-edge,0.0)*8.0)*0.40*(0.5+energy);
 float3 col=mix(cLight,c,m)+spec*m;
 return pm(col,max(m,glow));}

""" + TAIL

    val FEM2_VOICE = HEAD + """
float4 shade(float2 p){
 float a=atan(p.y,p.x);float r=length(p);
 float3 col=float3(0.0);float sw=0.0;
 for(int i=0;i<4;i++){float fi=float(i);
  float ph=t*(0.8+0.3*fi)+fi*1.7;
  float amp=(0.02+0.05*energy+0.09*level)*(0.6+0.4*sin(t*1.7+fi));
  float rr=0.26+0.10*fi+amp*sin(a*(3.0+fi)+ph)+amp*0.6*sin(a*(5.0+fi)-ph*1.3);
  float d=abs(r-rr);
  float w=exp(-d*d*2200.0)+0.22*exp(-d*d*140.0);
  float3 cc=mix(cMid,cLight,fi/3.0);
  col+=cc*w;sw+=w;}
 float cw=exp(-r*r*55.0);col+=cCore*cw*1.2;sw+=cw*1.2;
 return pm(col/max(sw,0.001),sw);}

""" + TAIL

    val FEM3_PEARL = HEAD + """
float4 shade(float2 p){
 float R=0.6;float r=length(p);float k=r/R;
 float m=1.0-smoothstep(0.985,1.0,k);
 float z=sqrt(max(1.0-k*k,0.0));float3 n=float3(p/R,z);
 float3 L=normalize(float3(-0.5,-0.6,0.7));
 float diff=clamp(dot(n,L),0.0,1.0);
 float fres=pow(1.0-z,2.5);
 float s1=sin(n.x*3.0+n.y*2.0+t*0.9)*0.5+0.5;float s2=sin(n.y*4.0-n.x*1.5-t*0.7)*0.5+0.5;
 float3 body=mix(mix(cLight,cMid,s1),cDeep,s2*0.55)*(0.55+0.5*diff);
 body=mix(body,cCore,exp(-k*k*5.0)*(0.35+0.45*energy));
 body+=fres*cLight*0.9;
 float spec=pow(clamp(dot(reflect(-L,n),float3(0.0,0.0,1.0)),0.0,1.0),40.0);
 body+=spec;
 float glow=exp(-max(r-R,0.0)*7.0)*0.30*(0.5+energy);
 float3 col=mix(cLight,body,m);
 return pm(col,max(m*(0.80+0.2*fres),glow));}

""" + TAIL

    val FEM4_MANDALA = HEAD + """
float4 shade(float2 p){
 float a=atan(p.y,p.x);float r=length(p);
 float3 col=float3(0.0);float sw=0.0;
 for(int i=0;i<3;i++){float fi=float(i);
  float kk=3.0+2.0*fi;
  float ph=t*(0.5+0.25*fi)*(mod(fi,2.0)<0.5?1.0:-1.0)+fi*1.1;
  float amp=0.20+0.10*energy+0.10*level;
  float rr=0.58*(0.60+amp*cos(kk*a+ph))*(0.9+0.1*fi*0.3);
  float d=abs(r-rr);
  float w=exp(-d*d*3400.0)+0.18*exp(-d*d*180.0);
  float3 cc=mix(cMid,cLight,fi/2.0);
  col+=cc*w;sw+=w;}
 float cw=exp(-r*r*60.0)*1.1;col+=cCore*cw;sw+=cw;
 return pm(col/max(sw,0.001),sw);}

""" + TAIL

}