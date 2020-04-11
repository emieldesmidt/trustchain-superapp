package nl.tudelft.trustchain.voting.ui.peers

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.mattskala.itemadapter.ItemAdapter
import kotlinx.android.synthetic.main.fragment_peers.*
import kotlinx.coroutines.*
import nl.tudelft.trustchain.voting.R
import nl.tudelft.trustchain.voting.ui.BaseFragment
import nl.tudelft.ipv8.util.toHex

class PeersFragment : BaseFragment() {
    private val adapter = ItemAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        adapter.registerRenderer(PeerItemRenderer {
            findNavController().navigate(
                PeersFragmentDirections.actionPeersFragmentToBlocksFragment(
                    it.peer.publicKey.keyToBin().toHex()
                )
            )
        })

        adapter.registerRenderer(AddressItemRenderer {
            // NOOP
        })
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_peers, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.addItemDecoration(DividerItemDecoration(context, LinearLayout.VERTICAL))

        loadNetworkInfo()
    }

    private fun loadNetworkInfo() {
        lifecycleScope.launchWhenStarted {
            while (isActive) {
                val overlays = getIpv8().overlays

                for ((_, overlay) in overlays) {
                    logger.debug(overlay.javaClass.simpleName + ": " + overlay.getPeers().size + " peers")
                }

                val votingCommunity = getVotingCommunity()
                val peers = votingCommunity.getPeers()

                val discoveredAddresses = votingCommunity.network
                    .getWalkableAddresses(votingCommunity.serviceId)

                val peerItems = peers.map { PeerItem(it) }

                val addressItems = discoveredAddresses.map { address ->
                    val contacted = votingCommunity.discoveredAddressesContacted[address]
                    AddressItem(address, null, contacted)
                }

                val items = peerItems + addressItems

                adapter.updateItems(items)
                txtCommunityName.text = votingCommunity.javaClass.simpleName
                txtPeerCount.text = resources.getQuantityString(
                    R.plurals.x_peers, peers.size,
                    peers.size
                )
                imgEmpty.isVisible = items.isEmpty()

                delay(1000)
            }
        }
    }
}
