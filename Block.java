import java.util.Set;

public final class Block {
   public final long ownerId;
   public final Block parent;
   public final Set<Simulation.Transaction> transactions;
   public final String message;
   public final int height;
   
   public Block(long ownerId, Block parent, Set<Simulation.Transaction> transactions, String message) {
      this.ownerId = ownerId;
      this.parent = parent;
      this.transactions = transactions;         
      this.message = message;
      this.height = 1 + (parent==null ? 1 : parent.height);
   }
   
   public Block(long ownerId, Block parent, Set<Simulation.Transaction> transactions) {
      this(ownerId, parent, transactions, null);
   }

   public int hashCode() {
      return transactions.hashCode();
   }
}