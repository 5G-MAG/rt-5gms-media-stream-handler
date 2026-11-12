package com.fivegmag.a5gmsmediastreamhandler.player

import android.telephony.CellIdentityGsm
import android.telephony.CellIdentityLte
import android.telephony.CellIdentityNr
import android.telephony.CellInfo
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import com.fivegmag.a5gmscommonlibrary.consumptionReporting.ConsumptionRequest
import com.fivegmag.a5gmscommonlibrary.models.CellIdentifierType
import com.fivegmag.a5gmscommonlibrary.models.Snssai
import com.fivegmag.a5gmscommonlibrary.models.TypedLocation
import java.lang.Exception

abstract class ConsumptionReporter {

    abstract fun initialize()

    /** Called when the UE moves to a location that it was not at before (TS 26.510 V19.2.0 clause 5.3.6.1: a trigger of a consumption report) */
    var locationChangeListener: (() -> Unit)? = null

    abstract fun getConsumptionReport(
        reportingClientId: String,
        consumptionRequest: ConsumptionRequest
    ): String

    abstract fun resetState()

    /** The access network changed: the units that are being consumed end and new ones begin on the new access network */
    abstract fun onAccessNetworkChanged()

    /** The network slice and the Data Network of the media access from now on (the units that start from now on carry them) */
    abstract fun setAccessNetworkIdentity(sliceInfo: Snssai?, dataNetworkName: String?)

    abstract fun reset()

    fun createTypedLocationByCellInfo(cellInfo: CellInfo): TypedLocation? {
        try {
            val typedLocation: TypedLocation?
            when (cellInfo) {
                // CGI = MCC + MNC + LAC + CI
                is CellInfoGsm -> {
                    val cellIdentity = cellInfo.cellIdentity as CellIdentityGsm
                    val mcc = cellIdentity.mccString
                    val mnc = cellIdentity.mncString
                    val lac = cellIdentity.lac
                    val ci = cellIdentity.cid
                    typedLocation = TypedLocation(CellIdentifierType.CGI, "$mcc$mnc$lac$ci")
                }
                // ECGI = MCC + MNC + ECI
                is CellInfoLte -> {
                    val cellIdentity = cellInfo.cellIdentity as CellIdentityLte
                    val mcc = cellIdentity.mccString
                    val mnc = cellIdentity.mncString
                    val eci = cellIdentity.ci
                    typedLocation = TypedLocation(CellIdentifierType.ECGI, "$mcc$mnc$eci")
                }
                // NCGI = MCC + MNC + NCI
                is CellInfoNr -> {
                    val cellIdentity = cellInfo.cellIdentity as CellIdentityNr
                    val mcc = cellIdentity.mccString
                    val mnc = cellIdentity.mncString
                    val nci = cellIdentity.nci
                    typedLocation = TypedLocation(CellIdentifierType.NCGI, "$mcc$mnc$nci")
                }

                else -> {
                    return null
                }
            }

            return typedLocation
        } catch (e: Exception) {
            return null
        }
    }
}