package com.fivegmag.a5gmsmediastreamhandler.player

import android.telephony.CellIdentityGsm
import android.telephony.CellIdentityLte
import android.telephony.CellIdentityNr
import android.telephony.CellInfo
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import com.fivegmag.a5gmscommonlibrary.consumptionReporting.PlaybackConsumptionReportingConfiguration
import com.fivegmag.a5gmscommonlibrary.models.CellIdentifierType
import com.fivegmag.a5gmscommonlibrary.models.TypedLocation
import java.lang.Exception

abstract class ConsumptionReporter {

    abstract fun initialize()

    abstract fun getConsumptionReport(
        reportingClientId: String,
        playbackConsumptionReportingConfiguration: PlaybackConsumptionReportingConfiguration
    ): String

    abstract fun resetState()

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