package nl.tudelft.trustchain.voting

import android.app.Application
import android.util.Log
import androidx.preference.PreferenceManager
import com.squareup.sqldelight.android.AndroidSqliteDriver
import nl.tudelft.ipv8.*
import nl.tudelft.ipv8.android.IPv8Android
import nl.tudelft.trustchain.voting.service.VotingService
import nl.tudelft.ipv8.android.keyvault.AndroidCryptoProvider
import nl.tudelft.ipv8.attestation.trustchain.*
import nl.tudelft.ipv8.attestation.trustchain.store.TrustChainSQLiteStore
import nl.tudelft.ipv8.attestation.trustchain.store.TrustChainStore
import nl.tudelft.ipv8.attestation.trustchain.validation.TransactionValidator
import nl.tudelft.ipv8.keyvault.PrivateKey
import nl.tudelft.ipv8.peerdiscovery.DiscoveryCommunity
import nl.tudelft.ipv8.peerdiscovery.strategy.PeriodicSimilarity
import nl.tudelft.ipv8.peerdiscovery.strategy.RandomChurn
import nl.tudelft.ipv8.peerdiscovery.strategy.RandomWalk
import nl.tudelft.ipv8.sqldelight.Database
import nl.tudelft.ipv8.util.hexToBytes
import nl.tudelft.ipv8.util.toHex
import nl.tudelft.trustchain.common.util.VotingHelper

class VotingApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        initIPv8()
    }

    private fun initIPv8() {
        val config = IPv8Configuration(
            overlays = listOf(
                createDiscoveryCommunity(),
                createTrustChainCommunity()
            ), walkerInterval = 1.0
        )

        IPv8Android.Factory(this)
            .setConfiguration(config)
            .setPrivateKey(getPrivateKey())
            .setServiceClass(VotingService::class.java)
            .init()

        initTrustChain()
    }

    private fun initTrustChain() {
        val ipv8 = IPv8Android.getInstance()
        val trustchain = ipv8.getOverlay<TrustChainCommunity>()!!

        trustchain.registerTransactionValidator(BLOCK_TYPE, object : TransactionValidator {
            override fun validate(
                block: TrustChainBlock,
                database: TrustChainStore
            ): Boolean {
                return block.transaction["message"] != null || block.isAgreement
            }
        })

        trustchain.registerBlockSigner(BLOCK_TYPE, object : BlockSigner {
            override fun onSignatureRequest(block: TrustChainBlock) {
//                trustchain.createAgreementBlock(block, mapOf<Any?, Any?>())
            }
        })

        trustchain.addListener(BLOCK_TYPE, object : BlockListener {
            override fun onBlockReceived(block: TrustChainBlock) {
                Log.d("TrustChainDemo", "onBlockReceived: ${block.blockId} ${block.transaction}")
            }
        })

        // Instantiate voter helper
        val trustchainvoter: VotingHelper = VotingHelper(trustChainCommunity = trustchain)

    }

    private fun createDiscoveryCommunity(): OverlayConfiguration<DiscoveryCommunity> {
        val randomWalk = RandomWalk.Factory()
        val randomChurn = RandomChurn.Factory()
        val periodicSimilarity = PeriodicSimilarity.Factory()
        return OverlayConfiguration(
            DiscoveryCommunity.Factory(),
            listOf(randomWalk, randomChurn, periodicSimilarity)
        )
    }

    private fun createTrustChainCommunity(): OverlayConfiguration<TrustChainCommunity> {
        val settings = TrustChainSettings()
        val driver = AndroidSqliteDriver(Database.Schema, this, "trustchain.db")
        val store = TrustChainSQLiteStore(Database(driver))
        val randomWalk = RandomWalk.Factory()
        return OverlayConfiguration(
            TrustChainCommunity.Factory(settings, store),
            listOf(randomWalk)
        )
    }

//    private fun createVotingCommunity(): OverlayConfiguration<VotingCommunity> {
//        val randomWalk = RandomWalk.Factory()
//        return OverlayConfiguration(
//            VotingCommunity.Factory(),
//            listOf(randomWalk)
//        )
//    }

    private fun getPrivateKey(): PrivateKey {
        // Load a key from the shared preferences
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val privateKey = prefs.getString(PREF_PRIVATE_KEY, null)
        return if (privateKey == null) {
            // Generate a new key on the first launch
            val newKey = AndroidCryptoProvider.generateKey()
            prefs.edit()
                .putString(PREF_PRIVATE_KEY, newKey.keyToBin().toHex())
                .apply()
            newKey
        } else {
            AndroidCryptoProvider.keyFromPrivateBin(privateKey.hexToBytes())
        }
    }

    companion object {
        private const val PREF_PRIVATE_KEY = "private_key"
        private const val BLOCK_TYPE = "voting_block"
    }
}
