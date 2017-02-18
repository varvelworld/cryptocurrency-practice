import java.util.*;

public class TxHandler {

    final private UTXOPool utxoPool;

    /**
     * Creates a public ledger whose current UTXOPool (collection of unspent transaction outputs) is
     * {@code utxoPool}. This should make a copy of utxoPool by using the UTXOPool(UTXOPool uPool)
     * constructor.
     */
    public TxHandler(UTXOPool utxoPool) {
        this.utxoPool = new UTXOPool(utxoPool);
    }

    /**
     * @return true if:
     * (1) all outputs claimed by {@code tx} are in the current UTXO pool,
     * (2) the signatures on each input of {@code tx} are valid,
     * (3) no UTXO is claimed multiple times by {@code tx},
     * (4) all of {@code tx}s output values are non-negative, and
     * (5) the sum of {@code tx}s input values is greater than or equal to the sum of its output
     *     values; and false otherwise.
     */
    public boolean isValidTx(Transaction tx) {
        Set<UTXO> uxtoSet = new HashSet<UTXO>();
        double sumOfInputValue = 0;
        int i = 0;
        for(Transaction.Input input : tx.getInputs()) {
            UTXO utxo = new UTXO(input.prevTxHash, input.outputIndex);
            Transaction.Output prevOutput = utxoPool.getTxOutput(utxo);
            if (prevOutput == null) return false;
            if(!Crypto.verifySignature(prevOutput.address, tx.getRawDataToSign(i), input.signature)) return false;
            if(!uxtoSet.add(utxo)) return false;
            sumOfInputValue += prevOutput.value;
            ++i;
        }
        double sumOfOutputValue = 0;
        for(Transaction.Output output : tx.getOutputs()) {
            if(output.value < 0) return false;
            sumOfOutputValue += output.value;
        }
        return sumOfInputValue >= sumOfOutputValue;
    }

    /**
     * Handles each epoch by receiving an unordered array of proposed transactions, checking each
     * transaction for correctness, returning a mutually valid array of accepted transactions, and
     * updating the current UTXO pool as appropriate.
     */
    public Transaction[] handleTxs(Transaction[] possibleTxs) {
        List<Transaction> acceptedTxs = new ArrayList<Transaction>();
        Set<Transaction> possibleTxSet = new HashSet<Transaction>(Arrays.asList(possibleTxs));
        while(!possibleTxSet.isEmpty()) {
            int oldSize = possibleTxSet.size();
            for (Transaction possibleTx : possibleTxSet) {
                Transaction tx = handleTx(possibleTx);
                if (tx == null) continue;
                acceptedTxs.add(tx);
                possibleTxSet.remove(possibleTx);
                break;
            }
            if(oldSize == possibleTxSet.size()) break;
        }
        return acceptedTxs.toArray(new Transaction[]{});
    }

    public Transaction handleTx(Transaction possibleTx) {
        if (!isValidTx(possibleTx)) return null;
        Transaction transaction = new Transaction(possibleTx);
        for (Transaction.Input input : transaction.getInputs()) {
            UTXO utxo = new UTXO(input.prevTxHash, input.outputIndex);
            utxoPool.removeUTXO(utxo);
        }
        int i = 0;
        transaction.computeHash();
        for (Transaction.Output output : transaction.getOutputs()) {
            UTXO utxo = new UTXO(transaction.getHash(), i);
            utxoPool.addUTXO(utxo, output);
            ++i;
        }
        return transaction;
    }

}
