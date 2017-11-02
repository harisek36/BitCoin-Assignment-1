import java.util.ArrayList;
import java.util.HashSet;

public class TxHandler {

   private UTXOPool utxoPool;

   // This constructor loads the UTXOpool
   /* Creates a public ledger whose current UTXOPool (collection of unspent
	 * transaction outputs) is utxoPool. This should make a defensive copy of
	 * utxoPool by using the UTXOPool(UTXOPool uPool) constructor.
	 */
   public TxHandler(UTXOPool up) {
      utxoPool = new UTXOPool(up);
   }

   // assuming all utxo's required by this transaction will be in utxo pool
   /* Returns true if
	 * (1) all outputs claimed by tx are in the current UTXO pool,
	 * (2) the signatures on each input of tx are valid,
	 * (3) no UTXO is claimed multiple times by tx,
	 * (4) all of tx’s output values are non-negative, and
	 * (5) the sum of tx’s input values is greater than or equal to the sum of
	        its output values;
	   and false otherwise.
	 */
   public boolean isValidTx(Transaction tx) {

     double totalInput = 0;
     HashSet<UTXO> utxosSeen = new HashSet<UTXO>();
     for (int i = 0; i < tx.getInputs().size(); i++) {
        Transaction.Input in = tx.getInput(i);
        UTXO ut = new UTXO(in.prevTxHash, in.outputIndex);
        // HashSet contains only unique copies of Tx, if we are able to add UTXO
        // with the same intput( with prevTxHash, OutputIndex) then the Tx is Invalid and return False
        if (!utxosSeen.add(ut))
           return false;
        Transaction.Output op = utxoPool.getTxOutput(ut);
        if (op == null)
           return false;

        // verify the Signature by comparing Message Digest and Input Private Key
        RSAKey address = op.address;  // Assigning TX -> Output's Public Key(Recipient) to RSAKey Address object
        if (!address.verifySignature(tx.getRawDataToSign(i), in.signature))  //Validating by comparing
                                                // Raw message with the input Private Key
            return false;
        totalInput += op.value;
     }
     double totalOutput = 0;
     ArrayList<Transaction.Output> txOutputs = tx.getOutputs();
     //Validating all of tx’s output values are non-negative
     for (Transaction.Output op : txOutputs) {
        if (op.value < 0)
           return false;
        totalOutput += op.value;
     }
     //the sum of tx’s input values is greater than or equal to the sum of its output values;
     return (totalInput >= totalOutput);


   }



   /* Handles each epoch by receiving an unordered array of proposed
	 * transactions, checking each transaction for correctness,
	 * returning a mutually valid array of accepted transactions,
	 * and updating the current UTXO pool as appropriate.
	 */

   private boolean inPool(Transaction tx) {
      // Storing the Tx input locally as inputs Object
      ArrayList<Transaction.Input> inputs = tx.getInputs();
      // Creating an Object "in" to compare each Tx input , to check wheather the Tx is in UTXO Pool
      Transaction.Input in;
      UTXO ut;
      for (int i = 0; i < inputs.size(); i++) {
         in = inputs.get(i);  // from the ArrayList of Tx's ->input , get a particular input and store it in "in"
         ut = new UTXO(in.prevTxHash, in.outputIndex);  // Create an instance of UTXO with inputs(prevHash and Output Index)
         if (!utxoPool.contains(ut))
            return false;
      }
      return true;
   }



   // do not change actual utxo pool because maintained a separate copy
   public Transaction[] handleTxs(Transaction[] OriginalTXs) {


         // Making a local copy of the received Original Transactions
         Transaction[] OriginalTx_Copy = new Transaction[OriginalTXs.length];

         // Iterating  to the Original Tx and make a local Copy
         for (int i = 0; i < OriginalTXs.length; i++)
            OriginalTx_Copy[i] = OriginalTXs[i];


         //FailedTxs object of class Transaction ,used to store the failed or inValid Transactions
         Transaction[] FailedTxs_Copy = new Transaction[OriginalTXs.length];

         //PassedTxs object of class Transaction ,used to store the passed or Valid Transactions
         Transaction[] PassedTxs_Copy = new Transaction[OriginalTXs.length];

         int failedTxCounter = 0;   // failed or inValid Transactions Counter, initially set to Zero
         int passedTxCounter = 0;   // passed or Valid Transactions Counter
         int validationTxSize = OriginalTXs.length;  // Initially set to received Original Tx[] Size, but changes
                                                     // after encountering failed Txs

         while (true) {               // If non of the tx in the Tx[] are in the pool, the loop exits

            boolean change = false;  // Set to false initially, but if anyone of the Tx in the Tx[] is Valid ,then its set to True
            failedTxCounter = 0;     // Set to Zero before validating a set of Transactions

            for (int i = 0; i < validationTxSize; i++) {

               if (inPool(OriginalTx_Copy[i])) {

                  if (isValidTx(OriginalTx_Copy[i])) {

                     change = true;

                     //Since the input Tx is Valid, remove the corresponding in Tx from UXTo Pool and add the Corresponding output to the UXTO Pool
                     for (int input_index = 0; input_index< OriginalTx_Copy[i].getInputs().size(); input_index++) {
                        Transaction.Input in = OriginalTx_Copy[i].getInput(input_index);
                        utxoPool.removeUTXO(new UTXO(in.prevTxHash, in.outputIndex));
                     }
                     for (int output_index = 0; output_index < OriginalTx_Copy[i].getOutputs().size(); output_index++) {
                        Transaction.Output out = OriginalTx_Copy[i].getOutput(output_index);
                        utxoPool.addUTXO(new UTXO(OriginalTx_Copy[i].getHash(), output_index), out);
                     }
                     // Increment the PassedTx Counter and store the Passed Tx in the PassedTxs_Copy[]
                     PassedTxs_Copy[passedTxCounter++] = OriginalTx_Copy[i];
                  }
               } else {
                  // Increment the FailedTx Counter and store the Failed Tx in the failedTxs_Copy[]
                  FailedTxs_Copy[failedTxCounter++] = OriginalTx_Copy[i];
               }
            }
            // If  tx in the Tx[] are  not in the UTXOpool or if Tx are  inValid, then  the loop exits
            if (change) {
               // Here the failed Tx copy is set to Original Tx Copy, this one is to make sure that the FailedTX is actually Failed
               for (int i = 0; i < failedTxCounter; i++) {
                  OriginalTx_Copy[i] = FailedTxs_Copy[i];
               }

               validationTxSize = failedTxCounter;   // Setting FailedTxCounter to Validating , makes the
                                                    //  above for loop to run only for number of failed Tx times

            } else {
               break;
            }
         }

         //The Transaction[] object result now holds only the passed Tx from the OriginalTXs
         Transaction[] result = new Transaction[passedTxCounter];
         for (int i = 0; i < passedTxCounter; i++)
            result[i] = PassedTxs_Copy[i];

         return result;

       }
}
