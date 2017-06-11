package org.walleth.activities

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.support.v7.app.AppCompatActivity
import android.view.MenuItem
import com.github.salomonbrys.kodein.LazyKodein
import com.github.salomonbrys.kodein.android.appKodein
import com.github.salomonbrys.kodein.instance
import com.satoshilabs.trezor.lib.TrezorManager
import com.satoshilabs.trezor.lib.protobuf.TrezorMessage
import org.walleth.R
import org.walleth.data.addressbook.AddressBook
import org.walleth.data.keystore.WallethKeyStore
import org.walleth.functions.toHexString

private val ADDRESS_HEX_KEY = "address_hex"
fun Intent.hasAddressResult() = hasExtra(ADDRESS_HEX_KEY)
fun Intent.getAddressResult() = getStringExtra(ADDRESS_HEX_KEY)

class TrezorCommunicatorActivity : AppCompatActivity() {

    val addressBook: AddressBook by LazyKodein(appKodein).instance()
    val keyStore: WallethKeyStore by LazyKodein(appKodein).instance()

    val manager by lazy { TrezorManager(this) }
    val handler = Handler()

    enum class STATES {
        REQUEST_PERMISSION,
        INIT,
        READ
    }

    var state: STATES = STATES.REQUEST_PERMISSION

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_trezor)

        supportActionBar?.subtitle = "TREZOR Hardware Wallet"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        handler.post(
                object : Runnable {
                    override fun run() {
                        if (manager.tryConnectDevice()) {

                            val res = when (state) {
                                STATES.INIT -> manager.sendMessage(TrezorMessage.Initialize.getDefaultInstance())
                                STATES.READ -> manager.sendMessage(TrezorMessage.EthereumGetAddress.getDefaultInstance())

                                else -> null
                            }

                            if (res is TrezorMessage.EthereumAddress) {
                                val resultIntent = Intent()
                                resultIntent.putExtra(ADDRESS_HEX_KEY, res.address.toByteArray().toHexString())
                                setResult(Activity.RESULT_OK, resultIntent)
                                finish()
                            }
                            if (res is TrezorMessage.Features) {
                                if (res.pinProtection) {
                                    state = STATES.READ
                                    handler.post(this)

//                                    alert("PIN protected TREZORs are not yet supported")
                                } else {
                                    state = STATES.READ
                                    handler.post(this)
                                }
                            }
                        } else {
                            if (state == STATES.REQUEST_PERMISSION && manager.hasDeviceWithoutPermission(true)) {
                                manager.requestDevicePermissionIfCan(true)
                                state = STATES.INIT
                            }

                            handler.postDelayed(this, 1000)
                        }
                    }
                })
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        android.R.id.home -> {
            finish()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }
}
