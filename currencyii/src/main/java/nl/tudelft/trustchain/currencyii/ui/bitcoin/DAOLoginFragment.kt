package nl.tudelft.trustchain.currencyii.ui.bitcoin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import kotlinx.android.synthetic.main.fragment_dao_login_choice.*
import nl.tudelft.trustchain.currencyii.R
import nl.tudelft.trustchain.currencyii.coin.BitcoinNetworkOptions
import nl.tudelft.trustchain.currencyii.coin.WalletManagerAndroid
import nl.tudelft.trustchain.currencyii.coin.WalletManagerConfiguration
import nl.tudelft.trustchain.currencyii.ui.BaseFragment

/**
 * A simple [Fragment] subclass.
 * Use the [DAOLoginFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class DAOLoginFragment : BaseFragment(R.layout.fragment_dao_login_choice) {

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        load_existing_button.setOnClickListener {
            // Close the current wallet manager if there is one running, blocks thread until it is closed
            if (WalletManagerAndroid.isInitialized()) {
                WalletManagerAndroid.close()
            }

            val params = when (production_testnet_input_load_existing.isChecked) {
                true -> BitcoinNetworkOptions.TEST_NET
                false -> BitcoinNetworkOptions.PRODUCTION
            }
            val config = WalletManagerConfiguration(params)
            WalletManagerAndroid.Factory(this.requireContext().applicationContext)
                .setConfiguration(config).init()

            findNavController().navigate(
                DAOLoginFragmentDirections.actionDaoLoginChoiceToBitcoinFragment(
                    true
                )
            )
        }

        import_create_button.setOnClickListener {
            findNavController().navigate(DAOLoginFragmentDirections.actionDaoLoginChoiceToDaoImportOrCreate())
        }

        super.onActivityCreated(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        hideNavBar()

        return inflater.inflate(R.layout.fragment_dao_login_choice, container, false)
    }

    companion object {
        @JvmStatic
        fun newInstance() = DAOLoginFragment()
    }
}
