import java.util.ArrayList;
import java.util.HashSet;

public class PickOldestMiner extends Miner {
   private long myId;

   private boolean revealingStrategy;
   private boolean changingAddressStrategy;

   private int myHeight;
   private Block myMaxHeightBlock;

   HashSet<Simulation.Transaction> txPool;

   ArrayList<Block> blocksToPublish;

   public PickOldestMiner(Block genesisBlock) {
      super(genesisBlock);

      myId = Simulation.getNewId(this);

      myHeight = 1;
      myMaxHeightBlock = genesisBlock;
      
      txPool = new HashSet<Simulation.Transaction>();

      blocksToPublish = new ArrayList<Block>();
   }

   public void setRevealingStrategy(boolean parameter) {
      revealingStrategy = parameter;
   }

   public void setChangingAddressStrategy(boolean parameter) {
      changingAddressStrategy = parameter;
   }

   // Receive a transaction. 
   public void hearTransaction(Simulation.Transaction tx) {
      txPool.add(tx);
   }

   // Hear about a new block found by someone else;
   public void hearBlock(Block block) {
      // remove all transactions in the block from tx pool
      // this might not be an optimal strategy to handle tx fees but 
      // this selfish miner does not care about tx fees
      for (Simulation.Transaction tx : block.transactions)
         txPool.remove(tx);

      if (block.height <= myHeight)
         return;

      myHeight = block.height;
      myMaxHeightBlock = block;
   }

   public ArrayList<Block> publishBlock() {
      return blocksToPublish;
   }

   public Block findBlock() {
      if (changingAddressStrategy) 
         myId = Simulation.getNewId(this);

      String message = null;
      if (revealingStrategy) 
         message = "Picking Oldest Max Height Block";

      Block parent = myMaxHeightBlock;

      Block block = new Block(myId, parent, new HashSet<Simulation.Transaction>(txPool), message);
      txPool.clear();

      myHeight += 1;
      myMaxHeightBlock = block;
      blocksToPublish.add(block);
      return block;   
   }
}