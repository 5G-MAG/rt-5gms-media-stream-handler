package com.fivegmag.a5gmsmediastreamhandler.controller

import com.fivegmag.a5gmscommonlibrary.models.Snssai

/** The access network of the default connection of the UE changed while the application runs (TS 26.510 V19.2.0 clause 5.3.6.1) */
class AccessNetworkChangedEvent

/**
 * The network slice and the Data Network that the media is accessed through are now these (null: not known). The platform does not tell an
 * application, so whoever knows them gives them to MediaSessionHandlerAdapter.setAccessNetworkIdentity.
 */
class AccessNetworkIdentityEvent(val sliceInfo: Snssai?, val dataNetworkName: String?)
