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
 float tf=floor(t*14.0);
 float core=exp(-r*r*40.0);float halo=exp(-r*r*7.0)*0.45*(0.6+0.4*energy);
 float bolt=0.0;
 for(int i=0;i<16;i++){float fi=float(i);
  float ai=fi*0.3927+hash(float2(fi,tf))*0.35;
  float j1=(vnoise(float2(r*11.0+fi*7.0,tf*1.7+fi))-0.5)*0.7*r;
  float j2=(vnoise(float2(r*30.0+fi,tf*2.3))-0.5)*0.16*r;
  float ad=wrapa(a-ai)+j1+j2;
  float d=ad*r;
  float len=0.32+0.60*hash(float2(fi,tf*0.5))*(0.6+0.6*energy)+0.10*level;
  float env=smoothstep(0.03,0.10,r)*(1.0-smoothstep(len*0.55,len,r));
  bolt+=(exp(-d*d*16000.0)+0.45*exp(-d*d*900.0))*env;}
 bolt=min(bolt,1.5);
 float tot=core*1.3+halo+bolt;
 float3 cc=(cCore*core*1.3+cMid*halo+mix(cMid,cLight,0.45+0.5*exp(-r*r*12.0))*bolt)/max(tot,0.001);
 return pm(cc,tot);}

""" + TAIL

    val MALE2_RINGED = HEAD + """
float4 shade(float2 p){
 float r=length(p);float R=0.28*(1.0+0.06*level);
 float a=atan(p.y,p.x);float k=r/R;
 float3 pc=float3(0.0);float pa=0.0;
 if(k<1.0){float z=sqrt(1.0-k*k);
  float s=pow(0.5+0.5*sin(5.0*a-10.0*k+t*(1.0+energy)*2.0+fbm(float2(a*2.0,k*4.0-t))*3.0),2.5);
  pc=mix(mix(cMid,cDeep,k),cLight,s*0.75);pc=mix(pc,cCore,exp(-k*k*6.0)*0.8);pc+=cLight*pow(1.0-z,3.0)*0.5;pa=1.0-smoothstep(0.97,1.0,k);}
 float tl=0.50;float c=cos(tl);float sn=sin(tl);
 float2 q=float2(p.x*c+p.y*sn,(-p.x*sn+p.y*c));
 float qy=q.y*3.4;float e=length(float2(q.x,qy));
 float band=smoothstep(0.42,0.46,e)*(1.0-smoothstep(0.80,0.86,e));
 float ang=atan(qy,q.x);
 float grain=fbm(float2(ang*14.0-t*(1.2+energy),e*70.0));
 float gaps=0.55+0.45*sin(e*55.0+ang*3.0);
 float ra=band*smoothstep(0.30,0.75,grain)*gaps*(0.55+0.45*energy);
 float front=step(0.0,qy);
 float hidden=(1.0-front)*pa;
 ra*=(1.0-hidden);
 float3 rc=mix(cMid,cLight,grain);rc=mix(rc,cCore,step(0.86,grain)*0.8);
 float glow=exp(-max(r-R,0.0)*9.0)*0.35*(0.5+energy);
 float tot=pa+ra+glow;
 return pm((pc*pa*(1.0-ra*0.7)+rc*ra+cLight*glow)/max(tot,0.001),tot);}

""" + TAIL

    val MALE4_ATOM = HEAD + """
float wrapa(float x){return mod(x+3.14159,6.28318)-3.14159;}
float4 shade(float2 p){
 float r=length(p);
 float3 col=float3(0.0);float sw=0.0;
 for(int i=0;i<4;i++){float fi=float(i);
  float rot=fi*0.785+0.3;float c=cos(rot);float s=sin(rot);
  float2 q=float2(p.x*c+p.y*s,-p.x*s+p.y*c);
  float A=0.82-0.04*fi;float B=0.28+0.05*fi;
  float2 qe=float2(q.x/A,q.y/B);
  float e=(length(qe)-1.0)*B;
  float u=atan(qe.y,qe.x);
  float dir=(mod(fi,2.0)<0.5?1.0:-1.0);
  float head=t*(1.0+0.4*fi+1.2*energy)*dir+fi*1.9;
  float da=wrapa((head-u)*dir);
  float tail=exp(-da*1.8)*smoothstep(0.0,0.08,da)*(1.0-smoothstep(2.8,3.5,da));
  float line=exp(-e*e*1100.0);
  float w=line*(0.10+tail*1.1)+exp(-e*e*700.0)*tail*0.35;
  float hd=exp(-(da*da*60.0)-e*e*600.0)*1.6;
  float3 cc=mix(cMid,cLight,fi/3.0);
  col+=cc*w+cCore*hd;sw+=w+hd;}
 float core=exp(-r*r*90.0)*1.4;
 float pulse=0.5+0.5*sin(t*3.0);
 float halo=exp(-r*r*16.0)*(0.35+0.25*energy+0.2*level*pulse);
 col+=cCore*core+cMid*halo;sw+=core+halo;
 return pm(col/max(sw,0.001),sw);}

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

    val FEM4_BLOOM = HEAD + """
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

    val MALE3_APERTURE = HEAD + """
float4 shade(float2 p){
 float a=atan(p.y,p.x);float r=length(p);float R=0.50;
 float spin=t*(0.35+0.9*energy);
 float disc=1.0-smoothstep(R-0.012,R,r);
 float ang=a-spin;
 float open=0.085+0.045*energy+0.10*level+0.02*sin(t*1.3);
 float seg=mod(ang+6.28318,1.047198);
 float rp=open*0.866025/max(cos(seg-0.523599),0.2);
 float hole=1.0-smoothstep(rp-0.008,rp+0.004,r);
 float rimh=exp(-pow(max(r-rp,0.0)*60.0,2.0));
 float ang2=ang+2.2*r;
 float s3=sin(3.0*ang2);
 float blade=exp(-s3*s3*r*r*900.0)*smoothstep(rp,rp+0.03,r)*(1.0-smoothstep(0.42,0.47,r));
 float3 iris=mix(cLight,cMid,smoothstep(0.10,0.50,r));
 iris=mix(iris,cDeep,smoothstep(0.40,0.50,r)*0.8);
 float shade2=0.5+0.5*sin(3.0*ang2+1.0);
 iris=mix(iris,cLight,shade2*0.35*(1.0-smoothstep(0.1,0.45,r)));
 float rim=exp(-pow((r-0.485)*80.0,2.0))*0.8;
 float ring2=exp(-pow((r-0.40)*70.0,2.0))*0.35;
 float3 col=iris;
 col=mix(col,cCore,blade*0.85);
 col=mix(col,cCore,(rim+ring2));
 col=mix(col,cCore,hole);
 col+=cCore*rimh*0.35;
 float glow=exp(-max(r-R,0.0)*9.0)*0.50*(0.5+energy);
 float tot=disc+glow;
 return pm((col*disc+cLight*glow)/max(tot,0.001),tot);}
""" + TAIL

}