package com.maryvellous.lavablobs;

/**
 * Playable, deliberately provisional content: scoring targets will be calibrated
 * with local game-session measurements. Levels are sequential, endless is open.
 */
final class GameSpec {
    final int id, shape, minScore, extraScore, goalTier, mergeGoal;
    final String name;
    final boolean endless;
    final boolean trial;

    private GameSpec(int id, String name, int shape, int minScore, int extraScore,
            int goalTier, int mergeGoal, boolean endless) {
        this.id=id; this.name=name; this.shape=shape;
        this.minScore=minScore; this.extraScore=extraScore;
        this.goalTier=goalTier; this.mergeGoal=mergeGoal;
        this.endless=endless; this.trial=id<=-10;
    }

    static final GameSpec[] LEVELS = {
        new GameSpec(0,"Primo vasetto",14,1500,2200,5,0,false),
        new GameSpec(1,"La coppa",15,950,1450,6,0,false),
        new GameSpec(2,"Ampolla rovesciata",16,1600,2200,6,0,false),
        new GameSpec(3,"Gola stretta",17,1850,2500,7,0,false),
        new GameSpec(4,"La fiala",18,2200,2700,7,0,false),
        new GameSpec(5,"Onde",19,2000,2550,8,24,false),
        new GameSpec(6,"Pancia",20,2100,2800,8,30,false),
        new GameSpec(7,"Clessidra",21,1900,2550,8,36,false),
        new GameSpec(8,"Piccola goccia",22,1800,2450,9,40,false),
        new GameSpec(9,"Vaso lunare",23,2150,2850,9,44,false),
        new GameSpec(10,"Il nodo",24,2000,2750,9,48,false),
        new GameSpec(11,"Ultima danza",25,2150,2900,10,52,false)
    };

    static final GameSpec[] ENDLESS = {
        new GameSpec(-1,"Infinita · Vaso",0,0,0,0,0,true),
        new GameSpec(-2,"Infinita · Anfora",1,0,0,0,0,true),
        new GameSpec(-3,"Infinita · Onde",5,0,0,0,0,true)
    };

    // Side-by-side playtest: equal width and identical physics, different heights.
    // Neither trial scores nor completion can unlock story levels.
    static final GameSpec[] TRIALS = {
        new GameSpec(-10,"Prova A · 285",11,0,0,0,0,false),
        new GameSpec(-11,"Prova B · 340",12,0,0,0,0,false),
        new GameSpec(-12,"Prova C · 400",13,0,0,0,0,false)
    };

    int totalExtras() { return trial?0:(mergeGoal>0?3:2); }
    boolean passed(long score) {return trial||endless||score>=minScore;}
    int extrasMask(long score,int maxTier,int merges) {
        if(endless||trial)return 0;
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
        // New scoring scale: merge 1+1 => 2 is worth 10; each next
        // size is worth 1.6x, rounded to the nearest integer.
        double value=10.0*Math.pow(1.6,Math.max(0,fromTier));
        if(value>=2_000_000_000.0)return 2_000_000_000;
        return (int)Math.round(value);
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
