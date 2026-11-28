package com.maryvellous.lavablobs;

import org.junit.Test;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.Assert.*;

public class CheckpointWriterTest {
    private static void await(CountDownLatch latch){
        try{if(!latch.await(5,TimeUnit.SECONDS))throw new AssertionError("timeout");}
        catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError(e);}
    }

    @Test public void rapidOffersKeepOnlyLatestPendingSnapshot() throws Exception{
        ExecutorService executor=Executors.newSingleThreadExecutor();
        CountDownLatch started=new CountDownLatch(1),release=new CountDownLatch(1);
        List<Integer> writes=Collections.synchronizedList(new ArrayList<>());
        try{
            CheckpointWriter<Integer> writer=new CheckpointWriter<>(executor,n->{
                if(n==1){started.countDown();await(release);}
                writes.add(n);
            });
            writer.offer(1);await(started);
            writer.offer(2);writer.offer(3);writer.offer(4);
            Future<?> flush=writer.barrier();
            release.countDown();flush.get(5,TimeUnit.SECONDS);
            assertEquals(Arrays.asList(1,4),writes);
        }finally{release.countDown();executor.shutdownNow();}
    }

    @Test public void clearDiscardsPendingAndCannotBeOverwrittenByOldGeneration() throws Exception{
        ExecutorService executor=Executors.newSingleThreadExecutor();
        CountDownLatch started=new CountDownLatch(1),release=new CountDownLatch(1);
        List<Integer> writes=Collections.synchronizedList(new ArrayList<>());
        try{
            CheckpointWriter<Integer> writer=new CheckpointWriter<>(executor,n->{
                if(n==1){started.countDown();await(release);}
                writes.add(n);
            });
            writer.offer(1);await(started);writer.offer(2);
            Future<?> clear=writer.discardAndRun(()->writes.add(-1));
            release.countDown();clear.get(5,TimeUnit.SECONDS);
            // The owner cannot offer a new generation until clear returns.
            writer.offer(3);writer.barrier().get(5,TimeUnit.SECONDS);
            assertEquals(Arrays.asList(1,-1,3),writes);
        }finally{release.countDown();executor.shutdownNow();}
    }

    @Test public void recreatedOwnersShareSerializedDiskOrdering() throws Exception{
        ExecutorService executor=Executors.newSingleThreadExecutor();
        List<Integer> writes=Collections.synchronizedList(new ArrayList<>());
        try{
            CheckpointWriter<Integer> first=new CheckpointWriter<>(executor,writes::add);
            first.offer(1);first.barrier().get(5,TimeUnit.SECONDS);
            CheckpointWriter<Integer> second=new CheckpointWriter<>(executor,writes::add);
            second.discardAndRun(()->writes.add(-1)).get(5,TimeUnit.SECONDS);
            second.offer(2);second.barrier().get(5,TimeUnit.SECONDS);
            assertEquals(Arrays.asList(1,-1,2),writes);
        }finally{executor.shutdownNow();}
    }
}
