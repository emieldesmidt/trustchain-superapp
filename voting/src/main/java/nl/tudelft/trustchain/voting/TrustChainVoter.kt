package nl.tudelft.trustchain.voting

import android.util.Log
import nl.tudelft.ipv8.IPv8
import nl.tudelft.ipv8.android.IPv8Android
import nl.tudelft.ipv8.attestation.trustchain.EMPTY_PK
import nl.tudelft.ipv8.attestation.trustchain.TrustChainBlock
import nl.tudelft.ipv8.attestation.trustchain.TrustChainCommunity
import nl.tudelft.ipv8.keyvault.defaultCryptoProvider
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.*

class TrustChainVoter() {

    // Initialize TrustChainHelper so we can make use of the TrustChain
    protected val trustchain: TrustChainHelper by lazy {
        TrustChainHelper(getTrustChainCommunity())
    }

    protected fun getTrustChainCommunity(): TrustChainCommunity {
        return getIpv8().getOverlay()
            ?: throw IllegalStateException("TrustChainCommunity is not configured")
    }

    protected fun getIpv8(): IPv8 {
        return IPv8Android.getInstance()
    }

    /**
     * Starts a vote with given voters list and voting subject.
     */
    fun startVote(voters : List<String>, voteSubject: String) {
        // TODO: Add vote ID to increase probability of uniqueness.

        val voteList = JSONArray(voters)

        // Create a JSON object containing the vote subject
        val voteJSON = JSONObject()
            .put("VOTE_SUBJECT", voteSubject)
            .put("VOTE_LIST", voteList)

        // Put the JSON string in the transaction's 'message' field.
        val transaction = mapOf("message" to voteJSON.toString())

        trustchain.createVoteProposalBlock(EMPTY_PK, transaction, "voting_block")
    }

    /**
     * Respond to a vote by checking whether the proposalblock is a voting block
     * and if it is, signing it with the reply.
     */
    fun respondToVote(voteName: String, vote: Boolean, proposalBlock: TrustChainBlock) {
        // Reply to the vote with YES or NO.
        val voteReply = if (vote) "YES" else "NO"

        // Create a JSON object containing the vote subject and the reply.
        val voteJSON = JSONObject()
            .put("VOTE_SUBJECT", voteName)
            .put("VOTE_REPLY", voteReply)

        // Put the JSON string in the transaction's 'message' field.
        val transaction = mapOf("message" to voteJSON.toString())

        trustchain.createAgreementBlock(proposalBlock, transaction)
    }

    /**
     * Return the tally on a vote proposal in a pair(yes, no).
     */
    fun countVotes(voters: List<String>, voteName: String, proposerKey: ByteArray): Pair<Int, Int> {

        // ArrayList for keeping track of already counted votes
        val votes: MutableList<String> = ArrayList()

        var yesCount = 0
        var noCount = 0

        // Crawl the chain of the proposer.
        for (it in trustchain.getChainByUser(proposerKey)) {
            val blockPublicKey = defaultCryptoProvider.keyFromPublicBin(it.publicKey).toString()

            // Check whether vote has already been counted
            if (votes.contains(it.publicKey.contentToString())){
                continue
            }

            // Skip all blocks which are not voting blocks
            // and don't have a 'message' field in their transaction.
            if (it.type != "voting_block" || !it.transaction.containsKey("message")) {
                continue
            }

            // Parse the 'message' field as JSON.
            val voteJSON = try {
                JSONObject(it.transaction["message"].toString())
            } catch (e: JSONException) {
                // Assume a malicious vote if it claims to be a vote but does not contain
                // proper JSON.
                handleInvalidVote("Block was a voting block but did not contain " +
                    "proper JSON in its message field: ${it.transaction["message"].toString()}."
                )
                continue
            }

            // Assume a malicious vote if it does not have a VOTE_SUBJECT.
            if (!voteJSON.has("VOTE_SUBJECT")) {
                handleInvalidVote("Block type was a voting block but did not have a VOTE_SUBJECT.")
                continue
            }

            // A block with another VOTE_SUBJECT belongs to another vote.
            if (voteJSON.get("VOTE_SUBJECT") != voteName) {
                // Block belongs to another vote.
                continue
            }

            // A block with the same subject but no reply is the original vote proposal.
            if (!voteJSON.has("VOTE_REPLY")) {
                // Block is the initial vote proposal because it does not have a VOTE_REPLY field.
                continue
            }

            // Check whether the voter is in voting list
            if (!voters.contains(blockPublicKey)){
                continue
            }

            // Add the votes, or assume a malicious vote if it is not YES or NO.
            when (voteJSON.get("VOTE_REPLY")) {
                "YES" -> {
                    yesCount++
                    votes.add(it.publicKey.contentToString())
                }
                "NO" -> {
                    noCount++
                    votes.add(it.publicKey.contentToString())
                }
                else -> handleInvalidVote("Vote was not 'YES' or 'NO' but: '${voteJSON.get("VOTE_REPLY")}'.")
            }
        }

        return Pair(yesCount, noCount)
    }

    /**
     * Log invalid vote.
     */
    private fun handleInvalidVote(errorType: String) {
        Log.e("vote_debug", errorType)
    }
}
