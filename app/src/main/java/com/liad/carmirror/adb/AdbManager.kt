package com.liad.carmirror.adb

import android.content.Context
import android.os.Build
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Date

/**
 * ADB client identity for Car Mirror. The key is created once and kept in the app's private
 * storage, so pairing with Wireless debugging only has to happen once (it survives updates).
 */
class AdbManager private constructor(context: Context) : AbsAdbConnectionManager() {

    companion object {
        @Volatile
        private var instance: AdbManager? = null

        fun get(context: Context): AdbManager =
            instance ?: synchronized(this) {
                instance ?: AdbManager(context.applicationContext).also { instance = it }
            }
    }

    private val key: PrivateKey
    private val cert: Certificate

    init {
        setApi(Build.VERSION.SDK_INT)
        val dir = File(context.noBackupFilesDir, "adb").apply { mkdirs() }
        val keyFile = File(dir, "key.pk8")
        val certFile = File(dir, "cert.der")
        if (keyFile.exists() && certFile.exists()) {
            key = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(keyFile.readBytes()))
            cert = certFile.inputStream().use { CertificateFactory.getInstance("X.509").generateCertificate(it) }
        } else {
            val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
            val now = System.currentTimeMillis()
            val name = X500Name("CN=CarMirror")
            val holder = JcaX509v3CertificateBuilder(
                name, BigInteger.valueOf(now),
                Date(now - 24L * 3600_000), Date(now + 20L * 365 * 24 * 3600_000),
                name, pair.public
            ).build(JcaContentSignerBuilder("SHA256withRSA").build(pair.private))
            key = pair.private
            cert = JcaX509CertificateConverter().getCertificate(holder)
            keyFile.writeBytes(key.encoded)
            certFile.writeBytes(cert.encoded)
        }
    }

    override fun getPrivateKey(): PrivateKey = key
    override fun getCertificate(): Certificate = cert
    override fun getDeviceName(): String = "CarMirror"
}
