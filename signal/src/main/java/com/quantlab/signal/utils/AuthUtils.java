package com.quantlab.signal.utils;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AuthUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthUtils.class);

    private static String activeProfile;

    @Value("${spring.profiles.active}")
    private String profile;

    @PostConstruct
    public void init() {
        activeProfile = profile;
    }


        public static String decryptData(String data) throws NoSuchPaddingException, IllegalBlockSizeException, NoSuchAlgorithmException, BadPaddingException, InvalidKeyException, InvalidKeySpecException {

        String privateKeyString = fetchPrivateKey();
        byte[] keyBytes = Base64.getDecoder().decode(privateKeyString.getBytes(StandardCharsets.UTF_8));
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory fact = KeyFactory.getInstance("RSA");
        PrivateKey PRIVATE_KEY = fact.generatePrivate(keySpec);
        byte[] encryptedMessage = Base64.getDecoder().decode(data);
        Cipher decryptionCipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        decryptionCipher.init(Cipher.DECRYPT_MODE, PRIVATE_KEY);
        byte[] decryptedMessage = decryptionCipher.doFinal(encryptedMessage);
        String decryptedData = new String(decryptedMessage);
        LOGGER.info("Decrypted string.. decryptedData: {}", decryptedData);
        return decryptedData;
    }

    private static String fetchPrivateKey() {

        if ("prod".equalsIgnoreCase(activeProfile)) {
            // Production private key
            return  "MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQCHHe7AafFdjbtzPhWognGdCvBFX8d7aa+bgcXAWE2S/K2MHkfFTpBVfRz0+V06SYYbhzWIxKhvdjew/QoWqbEkGDTCzhyNcIxBwE8ZP+fi1ZBc7sC27Boyi8kOpIRQZjD1r6H8XJR+zaL/5LyjwDpmvR8/a+30qNVVLa2/s3e4op5kMxefB55RwX7hp5UUE3Rwqrplsqu5h13/oIu29QkUddEAI4EoTJVthYJJGNs+KQytrumwUfet24C05Z6T/houMB1qKwik1ZCcyz3voCpziWhP9ZTH6ESz0TU40B5S2Jtyw4MjCwpdXJ/TsVdQRfRgMeJT/B9z7HPnJsmuh/kFAgMBAAECggEAMRWupjfWdyYF8tebBpxcqbBPmOQyk6LRKKz8KePqSnuAkGZvrD3eMTBCVLxCIeqTzWxRBl5q0hMgKCvEfncm4vXoNNyrgrZgPYKfOYz9sGnlB920JL+gxLwDTk51wr9dCePD9OmtCGOr00pFPTeX/7q0n1WWHEs5NLhjZkDMPUZLiK1xyb2W5FxHiBvDM5LQMiErUGGLvcXpbx6pQqeqXAHvWI//D6h4C3utzhg/4+R/TiVMmBQKD96U15TyP6NWh2ijBn/LDbFgnQgB3KPpy4zQxZd7HGs50Im1XCEomagjT3jBTTWYnPoFn+rLfbJyJjVVp8b+p0aTz2yLtePcAQKBgQDqLeKRF6VICgLYAZj01pbIgqSynpIwjYssQkkfsiRCudowa70HiA0zws9KOj0IFzNUbZrrFp7tV1mXMh64m1F2R8nLIp28YCvwKGjCAPjG/rf8prlZdSzVMD1c+nFfJUfGSwQ/qGxgjwwUKYUV8JJuAEVsPaq8grU8+MJqPTddHwKBgQCTtQOCfp3ba6rpPJSyp+q9FLfzSb3FKFq9jnVwek+aRXyJlcihMxdifAxQCfn0OdZfnqb5z7bNZIWlfNJ91gid71rauAmrF0gYutRjc8DIG3TmpVyReYu2cPWAcF1R0HuIK5MKS+wFIhI87yhZBkLWoC/yVVJZf5xtv5expytBWwKBgFaC+wdk999kK2bHOE2DFRMgCF4gbJtZPwfruP3nnrOz2yI2OxAUN+ReXJvsZe2ePI09LJUatz2xHlzX3DeQ0Yhjvu23bRRRZwoCs6iwGTfSk//XS/P7cMOVXaYCs21V5W0g+4HU46zMS8cLC6puRHo0yXDSW0fm9bN/prr+wHw7AoGAdOCdIFs0afWuTdSEDUJVurx0Omy1Xxbphmj9gL0n0/j77Jq7IOMes+1YiNT8FkbV3N/bXH0CN1A9eI9mt+/u7ZoCmGNcMye/AuKDQRbNCwq+2+spbmtxJDIJ8VH4Zc7EkSF5voIJBp7JM7JCiP8tUEt85RKikPMrQqSCUi7nSVUCgYEA45mmNj8P3UxGPIxZXlWeb/ZEclillWiYXggPF5i1K54Yb/znS/2zLPxK9xpykn7FkG2bpGcHAx8h1lqVaW0VZIAre8ZlER0q25pH1fEi0nxsbXV77n9v87vAkuyDNh8tvQCEzJC1EbFcED9UVB6G6bi09nDOeD9zsYIKPVxULBw=".replaceAll("\\s", "");
        } else {
            // UAT private key
            return  "MIIEvAIBADANBgkqhkiG9w0BAQEFAASCBKYwggSiAgEAAoIBAQC4TFyjDvqVKnHjxup/sBf9tmNKKAG5gVNSfzp2AHlOJOpUUKW4V4lViUmOmn4DmCp7fDPDgUuwsOeqvNS/ut4MITk4oqXY23O4koKcJWKJltl058HQaJvcCMBsxG/fMp/ItyDQUyohzrDbqFU7IRMtvt0FKG0aWXSZeN/EQE9GsY8pv/ubkhoga06Rf02959MBT2GG8E8CuMREx/FbXkqIYBOlTapmHzTSu3E6dz9xd9/hJbUsax/n8BpJwDTY4tcKzwNIDam4eZdqvupfrx7yHwLuGbxEZQVif+22rfJSm1C+DRHrv5RIFFm+lybeWuQKcsYAFloQEdJnCrCqpnBFAgMBAAECggEATTFe08VgW74tedRFQDpHsjMEn3jmUAbFGZbEq8xDAuIfbDVjVDUGBte8AYjgIy1HDfXV0JuFUnqmbjMtgSkJ/FamGvy1LoGslLB5GGHoRezzJC42Vc6bCax4vJYCraAMoQtjKR7MVOC33ylAmcWevNhmxtRJ7nFtjyK7xv2QtvDND7DyLzVlJai2NJDBSxOXnskAmxQ7hQjv5aXuMOzZxnaDxfSFF/BjhQDM88XrAadOKErGcf1I4tpfkUG88+RjKTGlvEqqOw1zwF26LUZR0ZDEdczMFxbwW8urLooiio16+Xd7i9njjGWYov/p/5o3+Yy9RxSkfLw3tWcUmUoMeQKBgQDygcJFjSVIyILDVMUaiaePlswMDDhAfiBqSTZI8+vTxYvHT87xpl2TiKUK5nrwRS4c22Wr0OqUsdA2uOhuV9vHuoi7GDCYKk+zyMZaD6OWLmlFqE0/zef/Ci1ifXacMgDO7Umc4I6rdQIqtskSxuetP89jhsSrF+XCvMjmWMTDJwKBgQDCjXyCXrUlsm2lgZAyYo5Q3FdIiLgIUEpW+WgtVF/95uGOv+vO2PXOFClLce7He6ZfCEczj1uC32Y0ElnQ2///36CDHAha72/wX6+vVyWZg+CCI+Vs0JZI7RdeytIevi0d8X5hCUZwiNM0YMn7/2MHKAECra0evRNhXWJk56ukswKBgDodAEochdfIPRXEBavTWvUc5O7qKrzBvNDblIes9FF+YH210Vi1Tm1hbHONQd68JU3DAb1Hxj810Tib3Q88PK3DNKrpJBQC57Ckp2vDnacKni2UJFbq5/KFJNUaTccmcG0mDeLrKSVLV+aWgN7gDXrF9NPb4ttBUcz14orYsOY3AoGAYzjll1fV6zPk19QMRqYdDwBRQ5z2vsa0I0dv6i5uBoAJ6VPYX9YnBsMhjGi+7t4RK8Z9Cb7DXSuyZDw3sl2BYm4a3sMdP8N9w4oJf9NiSbkId1b3W1fGTcsdcCrgnu0+9VaErdidJCekD8KNQQgu6Sdt3H/XGyYzDCUkWXIOCCECgYADJ4AXozNZ0k+nzQfWYdRO8pcWFiz5jLy3v2PqeB/1BLqPmbyrERfwfIRLt3XGnk7Lf+TsQ4n3Y7iedBUZTZvfCgx7oGb0n2LzgHD+tNjBchNMhp7jzsMX68iR7gYuUssGDQAQ6LTnSgrdqov+3jyP9ygwCv0aImt1mBrGkZk/8w==".replaceAll("\\s", "");

        }


        // LOGGER.info("Inside decryptData().. data: {}", data);
//         String privateKeyString = new String("MIIEvAIBADANBgkqhkiG9w0BAQEFAASCBKYwggSiAgEAAoIBAQC4TFyjDvqVKnHjxup/sBf9tmNKKAG5gVNSfzp2AHlOJOpUUKW4V4lViUmOmn4DmCp7fDPDgUuwsOeqvNS/ut4MITk4oqXY23O4koKcJWKJltl058HQaJvcCMBsxG/fMp/ItyDQUyohzrDbqFU7IRMtvt0FKG0aWXSZeN/EQE9GsY8pv/ubkhoga06Rf02959MBT2GG8E8CuMREx/FbXkqIYBOlTapmHzTSu3E6dz9xd9/hJbUsax/n8BpJwDTY4tcKzwNIDam4eZdqvupfrx7yHwLuGbxEZQVif+22rfJSm1C+DRHrv5RIFFm+lybeWuQKcsYAFloQEdJnCrCqpnBFAgMBAAECggEATTFe08VgW74tedRFQDpHsjMEn3jmUAbFGZbEq8xDAuIfbDVjVDUGBte8AYjgIy1HDfXV0JuFUnqmbjMtgSkJ/FamGvy1LoGslLB5GGHoRezzJC42Vc6bCax4vJYCraAMoQtjKR7MVOC33ylAmcWevNhmxtRJ7nFtjyK7xv2QtvDND7DyLzVlJai2NJDBSxOXnskAmxQ7hQjv5aXuMOzZxnaDxfSFF/BjhQDM88XrAadOKErGcf1I4tpfkUG88+RjKTGlvEqqOw1zwF26LUZR0ZDEdczMFxbwW8urLooiio16+Xd7i9njjGWYov/p/5o3+Yy9RxSkfLw3tWcUmUoMeQKBgQDygcJFjSVIyILDVMUaiaePlswMDDhAfiBqSTZI8+vTxYvHT87xpl2TiKUK5nrwRS4c22Wr0OqUsdA2uOhuV9vHuoi7GDCYKk+zyMZaD6OWLmlFqE0/zef/Ci1ifXacMgDO7Umc4I6rdQIqtskSxuetP89jhsSrF+XCvMjmWMTDJwKBgQDCjXyCXrUlsm2lgZAyYo5Q3FdIiLgIUEpW+WgtVF/95uGOv+vO2PXOFClLce7He6ZfCEczj1uC32Y0ElnQ2///36CDHAha72/wX6+vVyWZg+CCI+Vs0JZI7RdeytIevi0d8X5hCUZwiNM0YMn7/2MHKAECra0evRNhXWJk56ukswKBgDodAEochdfIPRXEBavTWvUc5O7qKrzBvNDblIes9FF+YH210Vi1Tm1hbHONQd68JU3DAb1Hxj810Tib3Q88PK3DNKrpJBQC57Ckp2vDnacKni2UJFbq5/KFJNUaTccmcG0mDeLrKSVLV+aWgN7gDXrF9NPb4ttBUcz14orYsOY3AoGAYzjll1fV6zPk19QMRqYdDwBRQ5z2vsa0I0dv6i5uBoAJ6VPYX9YnBsMhjGi+7t4RK8Z9Cb7DXSuyZDw3sl2BYm4a3sMdP8N9w4oJf9NiSbkId1b3W1fGTcsdcCrgnu0+9VaErdidJCekD8KNQQgu6Sdt3H/XGyYzDCUkWXIOCCECgYADJ4AXozNZ0k+nzQfWYdRO8pcWFiz5jLy3v2PqeB/1BLqPmbyrERfwfIRLt3XGnk7Lf+TsQ4n3Y7iedBUZTZvfCgx7oGb0n2LzgHD+tNjBchNMhp7jzsMX68iR7gYuUssGDQAQ6LTnSgrdqov+3jyP9ygwCv0aImt1mBrGkZk/8w==").replaceAll("\\s", "");
//  for the UAT OLD one       String privateKeyString = new String("MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQCFUzC5vjk/rlDNqzxP84y2iLpRcXd1fPB45IgUMpBa9kVIdPDwqnKDgUz6FeU2NgAc/cYeQig7vRzAoCzQS8jWwrQwcm1B7KCe4KzW0c7ZI7oWzFu9R7HMKGPnoEropXVRxDhVtXvxitQB9HqMHS50cGTUNZcUjY1JpAlTeeMZ0oio7bDHokfA+a5YlyWBkUBjEzjzYe1ODupoRicV4mqbXjKQsy+DWvmLxzzGGN9s7/CNlbD1nvdfOlsN9VCphQ4i9DJSHP4K9SHkl8YrWserAFOiKV1q5wKHOi2VFYKxc0GtednYXNCWfWwnQ6ParfgfB3kHBDEFHJCmXzD6ae8dAgMBAAECggEABPKRXFMyzKorVXaFRnJDRoLMZzlOtY1tXw8b/aPit31sFMRCLKwmk7q4oI8vclOOrYS9VWIfHBhoeL147EHjshSHm0SjyU5+p2avA0nF40jwHuh2fw+qCWl2S6InJXr+X4pojMWvU8dTFr97zLdIggmptDZI5Ppbw9/SeMaNJugbU+/rDRUuVhtgVJDlBI5kch/PlwLRcHVLTYOloT3ej3Xo5E2OKhjs1+kvlr5g3DbdozEoPkaJJEH2zRid/fgG+LoXiZkJXnX+UGCxPA1rh0OH8fHKA48s8whVsTtfs2sIBjJJ8vsBK1hfMw7Tz7eeC3z35kW2sCWmIEuTKEvwAQKBgQDTdBnjAdPDHQGJX/Rpq7c6BXdlosQiVdVZ+YXcKxOId4rJXvnAxb59uzSGp8R58ShebZd4MevKp0GcP3T41G3i28ec/UHSBMeelXLOzE7D+tVCuFhAOK/28mO8HO8itAlsnspyYMLLTIkqVlsifny/ntxVowbVELMSXbRH32frNQKBgQChaYnOFp/Xqa8LttKRKATd4+XAEgwPrWkrFZk5j34P5QocVGoD1HGl1P22IDpVNenm9gPBPsaavYobiYKLWYJwQFNK1nrcE7f1KjJFMAfZFwELSMrNeaszxR+hTe6ypLRq0qb3wS96g+WsZwK6xsdZ26QLeHOS6rFBBs3qYdcJSQKBgQC+AdPJIrkw2H2q/ehYlA6/Om5Bi/MGj255vThF0QdqV1EFBi0Sxh4YSQI4BcUNdmA6CexT+zfQEF9qkbMAbZKYibljHkfo+paNr+lMXlZg0KKVo6TZgJbOJpzsSmuwGWtmiYGNKdmgaRPQUWzvIA3k+lEvOqFHEAx5cm4vL1boCQKBgHx0Jme7tAUR9UC/9v0q8Q2xIT2F7LzNxTAOGDV0eQuCWthbEd7gF7x4TCL4GGIJDs8g0uZWI8W97NWsofYVzugEAPTMCLDVl1o17crA7Nwqy4hK2OuzKxjgnbG+qBQfwGg5Abo3vwbk2dqjFkyy5ZRYMNUTS3F0+bScrXpRc/7ZAoGBAM01y4Bi+nMjo85sWmV2zqImbSrg3GXMuq3+aepIOv22cdrjWefKLsNhdeQfPAWTXfh30iMEVzrtmnv2awqcWHF0qWVfloz0VPNivy2DA7PivH7uYyJPtebnrhTy9itFLVAkmyV2WmlTpDqd+uXtRmdvLn6yYHQ2iFO3pgm2xCUM").replaceAll("\\s", "");
//        String privateKeyString = new String("MIIEvAIBADANBgkqhkiG9w0BAQEFAASCBKYwggSiAgEAAoIBAQDJ9jBkRmYo51A4wUmLW4ksd3A6C2B3e7XwR+vbtzBjwFh8NujZHTMkudw2HuFNJA/Q8/hOn3baibIJatQd3hzsaKCF5nBSjxng/2OHmuRXXO8Ua2HB45pZBybAXNnPMzv4Kt/zUww+acRYHqeneoD2WtKdo82+PtQXItPosTipRLVSywRoaekwavWyBBcumGwwMlqdSedP9Jd2JeRyBvq5twe9H5JE+RF3HYj2VlRwmkWU+fXdVB+cxLiJ2Hv0f6j6oJXjwWpxLdWxkyZPv/NEZKMjPL0oFAuUA7oIu/2EGEDizvPn4vDJAmZvRtGUaScFHVOQrZIZkbSu4X3AVBMxAgMBAAECggEAGYme3t8kBeIR+8s9IHkHpG7J6x0LKrCi9eoyQcstPD/TMInE8cdGc0tpNBK9jIKV2WcwK8jwP3wP3i76+Xffkmk8exIDe/0KWbARhtEoNH/U33EgoGLGXqGP3d0f0Zp558yMGvmSX8YCBN6DdqL/8abUYQiYeIj7Sp2lzBO0xXpnxyMK5F43LfO0TzkYiTrjvV7efqPha18GiUIUMBvPuhKM/hq21nNrUuXlQEdje1ckeMlENHWxL8gyDoL6JQeie9S6VbnS7Iqg7SnnJrHGvTKqA8E32bTKiCG0lt32RBZhD9Dc7TNskXEDnfBBqub49ZxRiw82YoWVeFHTjSx6EQKBgQD/6IjaR5WmXy3lwDx+Vg1l9UaZZypDV7yGvLxO30RHXm/R3hm2+a/TFORyvRBNVW1y0XJNTuHh1NrA6Fj81T8m5ApHPn9jv67R6O3Fi0bn+XEXJ1nfU1xiOsRoRVP+xbJ+4SHMtU7DRhJmx8BGwzSdsSczeLLz5HKSpUFUN4/NHQKBgQDKCLU0XyJuXppRATaJa7qpDXXYVWfFWxb7rZ+pdjdyFfq4bi8xC2TeLUxzitbDVoeetqAQLQRO+RTxVoXPIasZiUA07fjioaGxF8vESEJiGXNHFRYwn4hrsOkrv66j84rSxlXsOSXe1ZX6b2YuC1rI4pxpM+mlCvSQeoaeZdPGJQKBgHQi5aiaABJG859mxI1ZJC93RpjrtsRlFsvW72aj8GqrMvf9YZImcmLLAnoHz1QpRshqwQLQn7ZLURNm3quvYz+VEg0PEeUE4qOkn/Ocp4szKt2lICY3wJ9bqDLh86KVudHW/e3XAm2gaU13rYzvnftKoE29owkITe67TJCZvosJAoGAZ63QT/ACW2FxD+Dve9YtFFQ0BbDOcBku0jlnli90hLeC0uK4lRKj0OCuOx0k2Vmuf2R4/9QaQezRMoiUYpeElrY4nPECqEsKDzeG+lOavX0SC4hmus0j1SAUiL71gG2a8E+YCIHLTW+OZ9aDOV3aWPzyB4r8fBYDK6G1M9RNuYECgYAqmUwVxC8E0GUOVz+FTMbvLPRSs1/sZjAa1bzd9cS9pyuQkZNG5fK3Hx8MYVIMFsZ43+/JlZvRu5gVEOAobDZBrpWYinvaqQ9jZa8QKAFyYsMKrhyDIlb1tXaqrWSaY6HuFVWcs7yPOioG9XAroGZS9CrJlgXMl4zUNmt0croWIw==").replaceAll("\\s", "");
// production one
//        String privateKeyString = new String("MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQCHHe7AafFdjbtzPhWognGdCvBFX8d7aa+bgcXAWE2S/K2MHkfFTpBVfRz0+V06SYYbhzWIxKhvdjew/QoWqbEkGDTCzhyNcIxBwE8ZP+fi1ZBc7sC27Boyi8kOpIRQZjD1r6H8XJR+zaL/5LyjwDpmvR8/a+30qNVVLa2/s3e4op5kMxefB55RwX7hp5UUE3Rwqrplsqu5h13/oIu29QkUddEAI4EoTJVthYJJGNs+KQytrumwUfet24C05Z6T/houMB1qKwik1ZCcyz3voCpziWhP9ZTH6ESz0TU40B5S2Jtyw4MjCwpdXJ/TsVdQRfRgMeJT/B9z7HPnJsmuh/kFAgMBAAECggEAMRWupjfWdyYF8tebBpxcqbBPmOQyk6LRKKz8KePqSnuAkGZvrD3eMTBCVLxCIeqTzWxRBl5q0hMgKCvEfncm4vXoNNyrgrZgPYKfOYz9sGnlB920JL+gxLwDTk51wr9dCePD9OmtCGOr00pFPTeX/7q0n1WWHEs5NLhjZkDMPUZLiK1xyb2W5FxHiBvDM5LQMiErUGGLvcXpbx6pQqeqXAHvWI//D6h4C3utzhg/4+R/TiVMmBQKD96U15TyP6NWh2ijBn/LDbFgnQgB3KPpy4zQxZd7HGs50Im1XCEomagjT3jBTTWYnPoFn+rLfbJyJjVVp8b+p0aTz2yLtePcAQKBgQDqLeKRF6VICgLYAZj01pbIgqSynpIwjYssQkkfsiRCudowa70HiA0zws9KOj0IFzNUbZrrFp7tV1mXMh64m1F2R8nLIp28YCvwKGjCAPjG/rf8prlZdSzVMD1c+nFfJUfGSwQ/qGxgjwwUKYUV8JJuAEVsPaq8grU8+MJqPTddHwKBgQCTtQOCfp3ba6rpPJSyp+q9FLfzSb3FKFq9jnVwek+aRXyJlcihMxdifAxQCfn0OdZfnqb5z7bNZIWlfNJ91gid71rauAmrF0gYutRjc8DIG3TmpVyReYu2cPWAcF1R0HuIK5MKS+wFIhI87yhZBkLWoC/yVVJZf5xtv5expytBWwKBgFaC+wdk999kK2bHOE2DFRMgCF4gbJtZPwfruP3nnrOz2yI2OxAUN+ReXJvsZe2ePI09LJUatz2xHlzX3DeQ0Yhjvu23bRRRZwoCs6iwGTfSk//XS/P7cMOVXaYCs21V5W0g+4HU46zMS8cLC6puRHo0yXDSW0fm9bN/prr+wHw7AoGAdOCdIFs0afWuTdSEDUJVurx0Omy1Xxbphmj9gL0n0/j77Jq7IOMes+1YiNT8FkbV3N/bXH0CN1A9eI9mt+/u7ZoCmGNcMye/AuKDQRbNCwq+2+spbmtxJDIJ8VH4Zc7EkSF5voIJBp7JM7JCiP8tUEt85RKikPMrQqSCUi7nSVUCgYEA45mmNj8P3UxGPIxZXlWeb/ZEclillWiYXggPF5i1K54Yb/znS/2zLPxK9xpykn7FkG2bpGcHAx8h1lqVaW0VZIAre8ZlER0q25pH1fEi0nxsbXV77n9v87vAkuyDNh8tvQCEzJC1EbFcED9UVB6G6bi09nDOeD9zsYIKPVxULBw=").replaceAll("\\s", "");

    }

    public static String encryptData(String data) throws CryptoException {
        LOGGER.info("Inside encryptData().. Plain text length: {}", data.length());

        try {
            // Load the public key from an external source (e.g., environment variable)
            String privateKeyString = new String("MIIEvAIBADANBgkqhkiG9w0BAQEFAASCBKYwggSiAgEAAoIBAQC4TFyjDvqVKnHjxup/sBf9tmNKKAG5gVNSfzp2AHlOJOpUUKW4V4lViUmOmn4DmCp7fDPDgUuwsOeqvNS/ut4MITk4oqXY23O4koKcJWKJltl058HQaJvcCMBsxG/fMp/ItyDQUyohzrDbqFU7IRMtvt0FKG0aWXSZeN/EQE9GsY8pv/ubkhoga06Rf02959MBT2GG8E8CuMREx/FbXkqIYBOlTapmHzTSu3E6dz9xd9/hJbUsax/n8BpJwDTY4tcKzwNIDam4eZdqvupfrx7yHwLuGbxEZQVif+22rfJSm1C+DRHrv5RIFFm+lybeWuQKcsYAFloQEdJnCrCqpnBFAgMBAAECggEATTFe08VgW74tedRFQDpHsjMEn3jmUAbFGZbEq8xDAuIfbDVjVDUGBte8AYjgIy1HDfXV0JuFUnqmbjMtgSkJ/FamGvy1LoGslLB5GGHoRezzJC42Vc6bCax4vJYCraAMoQtjKR7MVOC33ylAmcWevNhmxtRJ7nFtjyK7xv2QtvDND7DyLzVlJai2NJDBSxOXnskAmxQ7hQjv5aXuMOzZxnaDxfSFF/BjhQDM88XrAadOKErGcf1I4tpfkUG88+RjKTGlvEqqOw1zwF26LUZR0ZDEdczMFxbwW8urLooiio16+Xd7i9njjGWYov/p/5o3+Yy9RxSkfLw3tWcUmUoMeQKBgQDygcJFjSVIyILDVMUaiaePlswMDDhAfiBqSTZI8+vTxYvHT87xpl2TiKUK5nrwRS4c22Wr0OqUsdA2uOhuV9vHuoi7GDCYKk+zyMZaD6OWLmlFqE0/zef/Ci1ifXacMgDO7Umc4I6rdQIqtskSxuetP89jhsSrF+XCvMjmWMTDJwKBgQDCjXyCXrUlsm2lgZAyYo5Q3FdIiLgIUEpW+WgtVF/95uGOv+vO2PXOFClLce7He6ZfCEczj1uC32Y0ElnQ2///36CDHAha72/wX6+vVyWZg+CCI+Vs0JZI7RdeytIevi0d8X5hCUZwiNM0YMn7/2MHKAECra0evRNhXWJk56ukswKBgDodAEochdfIPRXEBavTWvUc5O7qKrzBvNDblIes9FF+YH210Vi1Tm1hbHONQd68JU3DAb1Hxj810Tib3Q88PK3DNKrpJBQC57Ckp2vDnacKni2UJFbq5/KFJNUaTccmcG0mDeLrKSVLV+aWgN7gDXrF9NPb4ttBUcz14orYsOY3AoGAYzjll1fV6zPk19QMRqYdDwBRQ5z2vsa0I0dv6i5uBoAJ6VPYX9YnBsMhjGi+7t4RK8Z9Cb7DXSuyZDw3sl2BYm4a3sMdP8N9w4oJf9NiSbkId1b3W1fGTcsdcCrgnu0+9VaErdidJCekD8KNQQgu6Sdt3H/XGyYzDCUkWXIOCCECgYADJ4AXozNZ0k+nzQfWYdRO8pcWFiz5jLy3v2PqeB/1BLqPmbyrERfwfIRLt3XGnk7Lf+TsQ4n3Y7iedBUZTZvfCgx7oGb0n2LzgHD+tNjBchNMhp7jzsMX68iR7gYuUssGDQAQ6LTnSgrdqov+3jyP9ygwCv0aImt1mBrGkZk/8w==").replaceAll("\\s", "");
            if (privateKeyString == null || privateKeyString.isEmpty()) {
                throw new CryptoException("Public key environment variable is not set.");
            }
            byte[] keyBytes = Base64.getDecoder().decode(privateKeyString.getBytes(StandardCharsets.UTF_8));
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
            KeyFactory fact = KeyFactory.getInstance("RSA");
            PrivateKey PRIVATE_KEY = fact.generatePrivate(keySpec);

            // Initialize the cipher for RSA encryption
            Cipher encryptionCipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            encryptionCipher.init(Cipher.ENCRYPT_MODE, PRIVATE_KEY);

            // Encrypt the plain text
            byte[] encryptedMessage = encryptionCipher.doFinal(data.getBytes(StandardCharsets.UTF_8));
            String encryptedData = Base64.getEncoder().encodeToString(encryptedMessage);

            LOGGER.info("Encryption successful.");
            return encryptedData;
        } catch (NoSuchPaddingException | NoSuchAlgorithmException | InvalidKeyException |
                 IllegalBlockSizeException | BadPaddingException | InvalidKeySpecException e) {
            LOGGER.error("Error during encryption: {}", e.getMessage());
            throw new CryptoException("Encryption failed due to a cryptographic error.", e);
        } catch (IllegalArgumentException e) {
            LOGGER.error("Base64 decoding failed: {}", e.getMessage());
            throw new CryptoException("Base64 decoding failed. Invalid public key format.", e);
        } catch (Exception e) {
            LOGGER.error("Unexpected error: {}", e.getMessage());
            throw new CryptoException("Unexpected error during encryption.", e);
        }
    }

    /**
     * Custom exception for encryption/decryption errors.
     */
    public static class CryptoException extends Exception {
        public CryptoException(String message) {
            super(message);
        }

        public CryptoException(String message, Throwable cause) {
            super(message, cause);
        }
    }


}
