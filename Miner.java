
import java.util.ArrayList;

public class Miner {
   
   public Miner(Block genesisBlock) {}
   
   // Receive a transaction. 
   public void hearTransaction(Simulation.Transaction tx) {}
   
   // Hear about a new block found by someone else;
   public void hearBlock(Block block) {}

   public ArrayList<Block> publishBlock() {
      return null;
   }
   
   public Block findBlock() {
      return null;
   }
   
   public void setRevealingStrategy(boolean parameter) {}
   
   public void setChangingAddressStrategy(boolean parameter) {}
}