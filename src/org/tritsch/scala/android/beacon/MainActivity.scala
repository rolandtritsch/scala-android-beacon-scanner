package org.tritsch.scala.android.beacon

import android.app.Activity
import android.bluetooth.{BluetoothAdapter, BluetoothDevice, BluetoothManager}
import android.content.{Context, Intent}
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.{Bundle, Handler, Message}
import android.text.TextUtils
import android.util.Log
import android.view.{Menu, MenuItem, LayoutInflater, View, ViewGroup, Window}
import android.widget.{ArrayAdapter, ListView, TextView, Toast}

import scala.collection.mutable

// import java.util.{HashMap, List, UUID}

// @todo - add copyright
// @todo - add license
// @todo - add documentation
// @todo - rewrite/refactor it into Scala
// @todo - add a progress/status indicator while scanning
// @todo - move all strings and constants into a resource file(s)
// @todo - refactor the implementation of the BLE interface into its own class or just a val (and use RunOnUIThread)
private object MainActivity {
  val TAG = classOf[MainActivity].getName

  // handler messages
  val MSG_CONNECT_AND_DISPLAY = 100
}

class MainActivity extends Activity with BluetoothAdapter.LeScanCallback {
  private var mBluetoothAdapter: BluetoothAdapter = null
  private var mAdapter: ProximityBeaconAdapter  = null
  private var mBeacons = new mutable.HashMap[String, ProximityBeacon]

  override def onCreate(savedInstanceState: Bundle): Unit = {
    Log.d(MainActivity.TAG, "Enter - onCreate")
    super.onCreate(savedInstanceState)

    requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS)
    setProgressBarIndeterminate(true)

    mAdapter = new ProximityBeaconAdapter(this)
    val list = new ListView(this)
    list.setAdapter(mAdapter)
    setContentView(list)

    val manager = getSystemService(Context.BLUETOOTH_SERVICE).asInstanceOf[BluetoothManager]
    mBluetoothAdapter = manager.getAdapter
    Log.d(MainActivity.TAG, "Leave - onCreate")
  }

  override def onCreateOptionsMenu(menu: Menu): Boolean = {
    Log.d(MainActivity.TAG, "Enter - onCreateOptionsMenu")
    getMenuInflater.inflate(R.menu.main, menu)
    Log.d(MainActivity.TAG, "Leave - onCreateOptionsMenu")
    true
  }

  override def onOptionsItemSelected(item: MenuItem): Boolean = {
    Log.d(MainActivity.TAG, "Enter - onOptionsItemSelected")

    val didIt = item.getItemId match {
      case R.id.action_start_scan => {
        Log.i(MainActivity.TAG, "Start scan ...")
        // @todo - disconnect existing beacons
        mBeacons = new mutable.HashMap[String, ProximityBeacon]
        resumeScan
        true
      }
      case R.id.action_stop_scan => {
        Log.i(MainActivity.TAG, "Stop scan ...")
        stopScan
        true
      }
      case _ => {
        assert(false, "FATAL: Unknown itemId")
        false
      }
    }

    super.onOptionsItemSelected(item)
    Log.d(MainActivity.TAG, "Leave - onOptionsItemSelected")
    didIt
  }

  override def onResume: Unit = {
    super.onResume
    Log.d(MainActivity.TAG, "Enter - onResume")
    Log.i(MainActivity.TAG, "Make sure BLE is available and enabled ...")

    if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled) {
      startActivity(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
      finish
    } else {
      if (!getPackageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
        Toast.makeText(this, "No BLE Support", Toast.LENGTH_SHORT).show
        finish
      } else {
        resumeScan
      }
    }
    Log.d(MainActivity.TAG, "Leave - onResume")
  }

  override def onPause: Unit = {
    super.onPause
    Log.d(MainActivity.TAG, "Enter - onPause")
    stopScan
    Log.d(MainActivity.TAG, "Leave - onPause")
  }

  private val mResumeRunnable = new Runnable {
    override def run: Unit = {
      resumeScan
    }
  }

  private val mSuspendRunnable = new Runnable {
    override def run: Unit = {
      suspendScan
    }
  }

  private def stopScan: Unit = {
    Log.d(MainActivity.TAG, "Enter - stopScan")
    mHandler.removeCallbacks(mResumeRunnable)
    mHandler.removeCallbacks(mSuspendRunnable)
    mBluetoothAdapter.stopLeScan(this)
    Log.d(MainActivity.TAG, "Leave - stopScan")
  }

  private def resumeScan: Unit = {
    Log.d(MainActivity.TAG, "Enter - resumeScan")
    mBluetoothAdapter.startLeScan(this)
    setProgressBarIndeterminateVisibility(true)
    mHandler.postDelayed(mSuspendRunnable, 1000)
    Log.d(MainActivity.TAG, "Leave - resumeScan")
  }

  private def suspendScan: Unit = {
    Log.d(MainActivity.TAG, "Enter - suspendScan")
    mBluetoothAdapter.stopLeScan(this)
    setProgressBarIndeterminateVisibility(false)
    mHandler.sendEmptyMessage(MainActivity.MSG_CONNECT_AND_DISPLAY)
    mHandler.postDelayed(mResumeRunnable, 1000)
    Log.d(MainActivity.TAG, "Leave - suspendScan")
  }

  // Implementation of BluetoothAdapter.LeScanCallback
  override def onLeScan(device: BluetoothDevice, rssi: Int, sr: Array[Byte]): Unit = {
    Log.d(MainActivity.TAG, "Enter - onLeScan")
    Log.i(MainActivity.TAG, s"Found device >${device.getAddress}/${device.getName}< @ >${rssi}< ...")
    Log.v(MainActivity.TAG, s"With ScanRecord >${ScanRecord.dump(sr)}< ...")

    // @todo - make scanrecord work
    // mBeacons.put(device.getAddress, new ProximityBeacon(s"${device.getName}", device.getAddress, rssi, ScanRecord(sr)))
    mBeacons.put(device.getAddress, new ProximityBeacon(s"${device.getName}", device.getAddress, rssi))
    Log.d(MainActivity.TAG, "Leave - onLeScan")
  }

  // Implememtation of message handler
  private val mHandler = new Handler {
    override def handleMessage(msg: Message): Unit = {
      msg.what match {
        case MainActivity.MSG_CONNECT_AND_DISPLAY => {
          Log.d(MainActivity.TAG, "Enter - MSG_CONNECT_AND_DISPLAY")
          //@ todo - iterate over all beacons and connect to the gatt server (to poll rssi with readRemoteRssi)
          mAdapter.setNotifyOnChange(false)
          mAdapter.clear
          for(b <- mBeacons.values) mAdapter.add(b)
          mAdapter.notifyDataSetChanged
          Log.d(MainActivity.TAG, "Leave - MSG_CONNECT_AND_DISPLAY")
        }
      }
    }
  }
}

private class ProximityBeaconAdapter(c: Context) extends ArrayAdapter[ProximityBeacon](c, 0) {
  override def getView(position: Int, convertView: View, parent: ViewGroup): View = {
    Log.d(MainActivity.TAG, "Enter - getView")

    var myConvertView: View = null
    if(convertView == null) {
      myConvertView = LayoutInflater.from(getContext).inflate(R.layout.item_beacon_list, parent, false)
    } else {
      myConvertView = convertView
    }

    val beacon = getItem(position)
    val textColor = getProximityColor(beacon.rssi)

    val nameView = myConvertView.findViewById(R.id.text_name).asInstanceOf[TextView]
    nameView.setText(beacon.name)
    nameView.setTextColor(textColor)

    val addressView = myConvertView.findViewById(R.id.text_address).asInstanceOf[TextView]
    addressView.setText(beacon.address.drop(12))
    addressView.setTextColor(textColor)

    val rssiView = myConvertView.findViewById(R.id.text_rssi).asInstanceOf[TextView]
    rssiView.setText(s"${beacon.rssi}dBm")
    rssiView.setTextColor(textColor)

    Log.d(MainActivity.TAG, "Leave - getView")
    myConvertView
  }

  private def getProximityColor(signal: Int): Int = {
    Log.d(MainActivity.TAG, "Enter - getProximityColor")

    var colorCode = 0
    if(signal > -50) colorCode = Color.rgb(255, 0, 0)
    else if(signal > -70) colorCode = Color.rgb(255, 0, 255)
    else if(signal > -90) colorCode = Color.rgb(0, 0, 255)
    else colorCode = Color.rgb(0, 0, 0)

    Log.i(MainActivity.TAG, s"Signal >${signal}< mapped to Color >${colorCode}< ...")
    Log.d(MainActivity.TAG, "Leave - getProximityColor")
    colorCode
  }
}
