import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

public class Simulation {

   static Random rand = new Random();
   static int numMiners = 9;
   static double[] hashPower = new double[9];
   static int[] settings = new int[8];
   static int[] blockArrivals;
   static double[] latency = new double[9];
   static boolean highTxFees = false;

   static int blockNum;
   static ArrayList<Map<Integer,Block>> blockDelivery;
   static Miner[] miners;
   static ArrayList<Set<Block>> pendingBlocks;
   static HashMap<Miner, Set<Long>> ownerIds;
   static HashMap<Block, Integer> blockCreator;
   static int time;
   static long blockReward = 2500000000L;

   static Block genesisBlock;
   static Block checkBlock;
   static Block maxHeightBlock;

   // set of transactions that have not been included between genesisBlock & checkBlock:
   static Set<Transaction> checkBlockUnspentTx = new HashSet<Transaction>();

   static ArrayList<Long> txOwnerIds = new ArrayList<Long>();

   public static long[] run(String args) {
      try {
         FileReader fileReader = new FileReader(args);
         BufferedReader bufferedReader = new BufferedReader(fileReader);

         // get hash powers:
         String line = bufferedReader.readLine();
         int i = 0;
         for(String s : line.split(" ")) {
            hashPower[i] = Double.parseDouble(s);
            i++;
         }
         // get settings:
         line = bufferedReader.readLine();
         i = 0;
         for(String s : line.split(" ")) {
            settings[i] = Integer.parseInt(s);
            i++;
         }         
         // get block arrival times:
         int numBlocks = Integer.parseInt(bufferedReader.readLine());
         blockArrivals = new int[numBlocks];
         line = bufferedReader.readLine();
         i = 0;
         for(String s : line.split(" ")) {
            blockArrivals[i] = Integer.parseInt(s);
            i++;
         }         
         // get latencies:
         line = bufferedReader.readLine();
         i = 0;
         for(String s : line.split(" ")) {
            latency[i] = Double.parseDouble(s);
            i++;
         }         
         // get tx fee setting:
         line = bufferedReader.readLine();
         if(line.equals("true")) highTxFees = true;
         
         blockNum = 0;
         miners = new Miner[numMiners];
         blockDelivery = new ArrayList<Map<Integer,Block>>(numMiners);
         pendingBlocks = new ArrayList<Set<Block>>(numMiners);
         ownerIds = new HashMap<Miner, Set<Long>>();
         blockCreator = new HashMap<Block, Integer>();
         time = 0;

         bufferedReader.close();            
      } catch(IOException e) {
         System.out.println("file not found");
      }

      // pendingBlocks have been successfully mined but not yet released by miner
      for(int i = 0; i < numMiners; i++) {
         blockDelivery.add(new HashMap<Integer,Block>());
         pendingBlocks.add(new HashSet<Block>());
      }

      for(int i = 0; i < 100; i++)
         txOwnerIds.add(rand.nextLong());

      genesisBlock = new Block(0, null, new HashSet<Transaction>());
      maxHeightBlock = genesisBlock;
      updateCheckBlock();

      miners[0] = new PreventHighTxFeeMiner(genesisBlock);
      miners[1] = new PreventHighTxFeeMiner(genesisBlock);
      miners[2] = new FeatherForkMiner(genesisBlock);
      miners[3] = new FeatherForkMiner(genesisBlock);
      miners[4] = new SelfishMiner(genesisBlock);
      miners[5] = new SelfishMiner(genesisBlock);
      miners[6] = new PickNewestMiner(genesisBlock);
      miners[7] = new PickOldestMiner(genesisBlock);
      miners[8] = new StudentMiner(genesisBlock);
      for (int i = 0; i <= 7; i++) {
         miners[i].setChangingAddressStrategy(settings[i] % 2 == 1);
         miners[i].setRevealingStrategy(settings[i] >= 2);
      }

      // generate random cutoff tx fee for the PreventHighTxFee miners
      for(int i = 0; i <= 1; i++) {
         long cutoff = (long) (rand.nextDouble() * 4900000000L) + 100000000L;
         ((PreventHighTxFeeMiner) miners[i]).setFeeCutOff(cutoff);
      }

      // generate random black lists for feather fork miners:
      for(int i = 2; i <= 3; i++) {
         HashSet<Long> blacklistedIds = new HashSet<Long>();
         for(long l : txOwnerIds)
            if (rand.nextDouble() < .33) blacklistedIds.add(l);
         ((FeatherForkMiner) miners[i]).setBlackListedTxOwnerIds(blacklistedIds);
      }

      int maxTime = blockArrivals[blockArrivals.length-1] + 100;

      for(time = 0; time < 20000; time++) {
         // generate a new random transaction and distribute to all miners
         Transaction tx = new Transaction();
         checkBlockUnspentTx.add(tx);
         for(Miner m : miners)
            m.hearTransaction(tx);
         for(int i = 0; i < numMiners; i++)
            publish(i);

         // handle case where a new block is found   
         if(blockArrivals[blockNum] == time) {
            blockNum++;
            int i = pickBlockWinner();
            Block newBlock = miners[i].findBlock();
            if (newBlock == null) continue;
            assert ownerIds.get(miners[i]).contains(newBlock.ownerId) : "Your miner does not own the id it published";
            // only accept if newBlock is a descendant of checkBlock and newBlock has no double spends
            assert verifyDescendance(newBlock) : "Ending runtime, because the block that your node proposed was more than 10 blocks behind the longest chain";
            assert verifyNoDoubleSpend(newBlock) : "Ending runtime, because the block that your node proposed tried to double-spend";
            blockCreator.put(newBlock, i);
            if(newBlock.height > maxHeightBlock.height) maxHeightBlock = newBlock;
            updateCheckBlock();
            pendingBlocks.get(i).add(newBlock);
            publish(i);
         }

         for(int i = 0; i < numMiners; i++)
            if(blockDelivery.get(i).containsKey(time))
               miners[i].hearBlock(blockDelivery.get(i).get(time));
      }

      long[] minerRevenue = new long[numMiners];
      for(Block b = maxHeightBlock; b.ownerId != 0; b = b.parent) {
         int i = blockCreator.get(b);
         minerRevenue[i] += blockReward;
         for(Transaction tx : b.transactions)
            minerRevenue[i] += tx.fee;
      }

      //for(long revenue : minerRevenue)
        // System.out.println((revenue*1.0)/100000000);
      return minerRevenue;
   }

   // newBlock must be a descendant of checkBlock
   private static boolean verifyDescendance(Block newBlock) {
      Block b = newBlock.parent;
      for(int i = 0; i < 10; i++) {
         if(b == checkBlock) return true;
         if(b == null) return false;
         b = b.parent;
      }
      return false;
   }

   private static boolean verifyNoDoubleSpend(Block newBlock) {
      Set<Transaction> unspentTx = new HashSet<Transaction>(checkBlockUnspentTx);
      for(Block b = newBlock; b != checkBlock; b = b.parent) {
         for(Transaction tx : b.transactions)
            unspentTx.remove(tx);
      }

      for(Transaction tx : newBlock.transactions)
         if(!unspentTx.contains(tx)) return false;
      return true;
   }

   private static void updateCheckBlock() {
      checkBlock = maxHeightBlock;
      for(int i = 0; i < 10; i++) {
         if (checkBlock.parent == null) return;
         checkBlock = checkBlock.parent;
      }
      for(Transaction tx : checkBlock.transactions)
         checkBlockUnspentTx.remove(tx);
   }

   private static void publish(int i) {
      ArrayList<Block> blocksToPublish = new ArrayList<Block>();
      ArrayList<Block> blocks = miners[i].publishBlock();
      if (blocks == null) return;
      for (Block b : blocks) {
         if(pendingBlocks.get(i).remove(b)) {
            assert !pendingBlocks.get(i).contains(b.parent) : "Your publishBlock list was out of order";
            blocksToPublish.add(b);
         }
      }

      // Now, queue up blocks for the other miners
      double R = Math.random()*1.5;
      for (int j = 0; j < numMiners; j++) {
         if(j==i) continue;
         int delay = (int) ((latency[i] + latency[j])*R);
         for(int dt = 0; dt < blocksToPublish.size(); dt++) {
            blockDelivery.get(j).put(time+delay+dt, blocksToPublish.get(dt));
         }
      }
   }

   private static int pickBlockWinner() {
      double rnd = Math.random();
      for(int i = 0; i < numMiners; i++) {
         rnd -= hashPower[i];
         if(rnd <= 0) return i;
      }
      return -1;
   }

   public static long getNewId(Miner m) {
      long newId = rand.nextLong();
      Set<Long> ids = ownerIds.get(m);
      if (ids == null) {
         ids = new HashSet<Long>();
      }
      ids.add(newId);
      ownerIds.put(m, ids);
      return newId;
   }

   public static double[] getHashPowerDistribution() {
      return hashPower;
   }

   public static final class Transaction {
      public final long id;
      public final long fee; // in satoshis
      public final long ownerId;

      private Transaction() { // random transaction
         this.id = rand.nextInt();
         if(highTxFees) {
            if(rand.nextDouble() < .1)
               this.fee = (long) (rand.nextDouble() * 5000000000L) + 2500000000L;
            else
               this.fee = rand.nextInt(10000000);
         } else {
            this.fee = rand.nextInt(10000000); // 0-.1 BTC
         }
         this.ownerId = txOwnerIds.get(rand.nextInt(txOwnerIds.size()));
      }

      public boolean equals(Object obj) {
         if (obj == null) {
            return false;
         }
         if (getClass() != obj.getClass()) {
            return false;
         }
         final Transaction other = (Transaction) obj;
         if (this.id != other.id) {
            return false;
         }
         return true;
      }

      public int hashCode() {
         return (int) id;
      }
   }
}

