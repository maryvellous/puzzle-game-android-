package com.maryvellous.lavablobs;

/**
 * Playable, deliberately provisional content: scoring targets will be calibrated
 * with local game-session measurements. Levels are sequential, endless is open.
 */
final class GameSpec {
    final int id, shape, minScore, extraScore, goalTier, mergeGoal;
    final String name;
    final boolean endless;

    private GameSpec(int id, String name, int shape, int minScore, int extraScore,
            int goalTier, int mergeGoal, boolean endless) {
        this.id=id; this.name=name; this.shape=shape;
        this.minScore=minScore; this.extraScore=extraScore;
        this.goalTier=goalTier; this.mergeGoal=mergeGoal; this.endless=endless;
    }

    static final GameSpec[] LEVELS = {
        new GameSpec(0,"Primo vasetto",0,1200,2600,5,0,false),
        new GameSpec(1,"La coppa",2,1700,3400,6,0,false),
        new GameSpec(2,"Anfora",1,2200,4500,6,0,false),
        new GameSpec(3,"Gola stretta",3,2800,5500,7,0,false),
        new GameSpec(4,"La fiala",4,3400,7000,7,0,false),
        new GameSpec(5,"Onde",5,4000,8500,8,24,false),
        new GameSpec(6,"Pancia",6,4700,10000,8,30,false),
        new GameSpec(7,"Clessidra",3,5400,12000,8,36,false),
        new GameSpec(8,"Piccola goccia",7,6200,13500,9,40,false),
        new GameSpec(9,"Vaso lunare",8,7000,15500,9,44,false),
        new GameSpec(10,"Il nodo",9,8200,17500,9,48,false),
        new GameSpec(11,"Ultima danza",10,9500,20500,10,52,false)
    };

    static final GameSpec[] ENDLESS = {
        new GameSpec(-1,"Infinita · Vaso",0,0,0,0,0,true),
        new GameSpec(-2,"Infinita · Anfora",1,0,0,0,0,true),
        new GameSpec(-3,"Infinita · Onde",5,0,0,0,0,true)
    };

    int totalExtras() { return mergeGoal>0?3:2; }
    boolean passed(long score) {return endless||score>=minScore;}
    int extrasMask(long score,int maxTier,int merges) {
        if(endless)return 0;
        int mask=0;
        if(score>=extraScore)mask|=1;
        if(maxTier+1>=goalTier)mask|=2;
        if(mergeGoal>0&&merges>=mergeGoal)mask|=4;
        return mask;
    }
    int extras(long score,int maxTier,int merges) {
        return Integer.bitCount(extrasMask(score,maxTier,merges));
    }
    static int pointsForMerge(int fromTier) {
        double value=100.0*Math.pow(1.6,Math.max(0,fromTier));
        if(value>=2_000_000_000.0)return 2_000_000_000;
        return (int)(Math.round(value/10.0)*10);
    }
    static int rollDrop(java.util.Random random) {
        int roll=random.nextInt(100);
        if(roll<45)return 0;
        if(roll<73)return 1;
        if(roll<89)return 2;
        if(roll<97)return 3;
        return 4;
    }
}
