public class Regul extends Thread {

    private PI inner = new PI();
    private PID outer = new PID();

    private BallBeamAnimator ballBeam;

    private ReferenceGenerator refGen;
    private OpCom opCom;

    private int priority;
    private boolean shouldRun = true;
    private long starttime;

    private ModeMonitor modeMon;

    public Regul(int pri, ModeMonitor modeMon) {
        priority = pri;
        setPriority(priority);

        ballBeam = new BallBeamAnimator(modeMon);

        this.modeMon = modeMon;
    }

    /** Sets OpCom (called from main) */
    public void setOpCom(OpCom opCom) {
        /** Written by you */
    	this.opCom = opCom;
    }

    /** Sets ReferenceGenerator (called from main) */
    public void setRefGen(ReferenceGenerator refGen) {
        /** Written by you */
    	this.refGen = refGen;
    }

    // Called in every sample in order to send plot data to OpCom
    private void sendDataToOpCom(double yRef, double y, double u) {
        double x = (double) (System.currentTimeMillis() - starttime) / 1000.0;
        opCom.putControlData(x, u);
        opCom.putMeasurementData(x, yRef, y);
    }

    // Sets the inner controller's parameters
    public void setInnerParameters(PIParameters p) {
        /** Written by you */
    	this.inner.setParameters(p);
    }

    // Gets the inner controller's parameters
    public PIParameters getInnerParameters() {
        /** Written by you */
    	return this.inner.getParameters();
    }

    // Sets the outer controller's parameters
    public void setOuterParameters(PIDParameters p) {
        /** Written by you */
    	this.outer.setParameters(p);
    }

    // Gets the outer controller's parameters
    public PIDParameters getOuterParameters(){
        /** Written by you */
    	return this.outer.getParameters();
    }

    // Called from OpCom when shutting down
    public void shutDown() {
        shouldRun = false;
    }

    // Saturation function
    private double limit(double v) {
        return limit(v, -10, 10);
    }

    // Saturation function
    private double limit(double v, double min, double max) {
        if (v < min) v = min;
        else if (v > max) v = max;
        return v;
    }

    public void run() {

        long duration;
        long t = System.currentTimeMillis();
        starttime = t;
        
        double y = 0;
        double yRef = 0;
        double u = 0;
        
        double PIy = 0;
        double PIref = 0;
        double PIu = 0;
        double uff;
        
        double PIDy = 0;
        double PIDref = 0;
        double PIDu = 0;
        double phiff;


        while (shouldRun) {
            /** Written by you */
        	yRef = this.refGen.getRef();
        	
            switch (modeMon.getMode()) {
                case OFF: {
                    /** Written by you */
                	y = 0;
                	u = 0;
                	yRef = 0;
                	this.inner.reset();
                	this.outer.reset();
                    break;
                }
                case BEAM: {
                	synchronized(inner) {
                		PIref = yRef;
                		uff = this.refGen.getUff();
                		PIy = this.ballBeam.getBeamAngle();
                		PIu = this.limit(this.inner.calculateOutput(PIy, PIref) + uff);
                    	this.ballBeam.setControlSignal(PIu);
                		this.inner.updateState(PIu - uff);	
                	}
                    y = PIy;
                    u = PIu;

                    break;
                }
                case BALL: {
                    /** Written by you */
                	
            		PIDref = yRef;
            		uff = this.refGen.getUff();
            		phiff = this.refGen.getPhiff();
                	PIDy = this.ballBeam.getBallPos();
                	synchronized(outer) {
                		PIDu = this.outer.calculateOutput(PIDy, PIDref)+phiff;
                		//PIref = this.limit(PIDu);
                		PIref = PIDu;
                		PIDu = (PIu >= 10 || PIu <= -10) ? this.ballBeam.getBeamAngle() - phiff: PIDu-phiff;

                		this.outer.updateState(this.limit(PIDu));

                	PIy = this.ballBeam.getBeamAngle();
                	
                	synchronized(inner) {
                		PIu = this.limit(this.inner.calculateOutput(PIy, PIref) + uff);
                    	this.ballBeam.setControlSignal(PIu);
                		this.inner.updateState(PIu-uff);
                	}
                	}
                	y = PIDy;
                	u = PIDu;
                	
                    break;
                }
                default: {
                    System.out.println("Error: Illegal mode.");
                    y = 0;
                    u = 0;
                    yRef = 0;
                    break;
                }
            }
            this.sendDataToOpCom(yRef, y, u);

            // sleep
            t = t + inner.getHMillis();
            duration = t - System.currentTimeMillis();
            System.out.println(duration);
            if (duration > 0) {
                try {
                    sleep(duration);
                } catch (InterruptedException x) {}
            } else {
                System.out.println("Lagging behind...");
                break;
            }
        }
        
        ballBeam.setControlSignal(0.0);
    }
}
