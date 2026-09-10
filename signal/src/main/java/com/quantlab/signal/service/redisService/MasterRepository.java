package com.quantlab.signal.service.redisService;
import com.quantlab.signal.dto.redisDto.MasterResponseFO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class MasterRepository {

        @Autowired
        @Qualifier("redisTemplate5")
        private final RedisTemplate<String, MasterResponseFO> redisTemplate;

       public MasterRepository(RedisTemplate<String, MasterResponseFO> redisTemplate) {
        this.redisTemplate = redisTemplate;
        }

        public void saveList(String key, List<MasterResponseFO> responses) {
            ListOperations<String, MasterResponseFO> listOps = redisTemplate.opsForList();
            listOps.rightPushAll(key, responses);
        }
    public void save(String key, MasterResponseFO masterResponseFO) {
        redisTemplate.opsForValue().set(key, masterResponseFO);
    }

    public MasterResponseFO find(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    public List<MasterResponseFO> findAll(String key) {
        ListOperations<String, MasterResponseFO> listOps = redisTemplate.opsForList();
        return listOps.range(key, 0, -1);
    }

    public void clearList(String key) {
        redisTemplate.delete(key);
    }


}

//    public void saveList(String key, List<MasterResponseFO> responses){
//
//           List<MasterResponseFO> filteredResponses = responses.stream()
//                   .filter(response -> "NIFTYNXT50".equals(response.getInstrumentType()) &&
//                                       "2024-10-25T14:30:00".equals(response.getContractExpiration()))
//                   .collect(Collectors.toList());
//
//           ListOperations<String, MasterResponseFO> listOps = redisTemplate.opsForList();
//           listOps.rightPushAll(key, filteredResponses);
//    }

//    List<Instrument> instruments = new ArrayList<>();
//        instruments.add(new Instrument("BANKNIFTY", "2024-10-30T14:30:00"));0
//        instruments.add(new Instrument("NIFTYNXT50", "2024-10-25T14:30:00"));1
//        instruments.add(new Instrument("NIFTY", "2024-10-31T14:30:00"));2
//        instruments.add(new Instrument("NIFTYNXT50", "2024-11-29T14:30:00"));3
//        instruments.add(new Instrument("MIDCPNIFTY", "2024-10-28T14:30:00"));4
//        instruments.add(new Instrument("BANKNIFTY", "2024-11-27T14:30:00"));5
//        instruments.add(new Instrument("NIFTY", "2024-11-28T14:30:00"));6
//        instruments.add(new Instrument("MIDCPNIFTY", "2024-11-25T14:30:00"));7
//        instruments.add(new Instrument("BANKNIFTY", "2024-12-24T14:30:00"));8
//        instruments.add(new Instrument("MIDCPNIFTY", "2024-12-30T14:30:00"));9
//        instruments.add(new Instrument("NIFTY", "2024-12-26T14:30:00"));10
//        instruments.add(new Instrument("FINNIFTY", "2024-10-29T14:30:00"));11
//        instruments.add(new Instrument("NIFTYNXT50", "2024-12-27T14:30:00"));12
//        instruments.add(new Instrument("FINNIFTY", "2024-11-26T14:30:00"));13
//        instruments.add(new Instrument("FINNIFTY", "2024-12-31T14:30:00"));14
//        instruments.add(new Instrument("BANKNIFTY", "2024-10-30T14:30:00"));15
//        instruments.add(new Instrument("MIDCPNIFTY", "2024-10-28T14:30:00"));16
//        instruments.add(new Instrument("NIFTYNXT50", "2024-11-29T14:30:00"));17
//        instruments.add(new Instrument("MIDCPNIFTY", "2024-10-31T14:30:00"));18
//        instruments.add(new Instrument("MIDCPNIFTY", "2024-10-28T14:30:00"));19
//        instruments.add(new Instrument("NIFTY", "2024-11-28T14:30:00"));20
//        instruments.add(new Instrument("BANKNIFTY", "2024-11-27T14:30:00"));21
//        instruments.add(new Instrument("MIDCPNIFTY", "2024-11-25T14:30:00"));22
//        instruments.add(new Instrument("NIFTYNXT50", "2024-10-25T14:30:00"));23
//        instruments.add(new Instrument("FINNIFTY", "2024-11-26T14:30:00"));24
//        instruments.add(new Instrument("NIFTY", "2024-10-31T14:30:00"));25
//        instruments.add(new Instrument("BANKNIFTY", "2024-10-30T14:30:00"));26
//        instruments.add(new Instrument("FINNIFTY", "2024-10-29T14:30:00"));27
//        instruments.add(new Instrument("NIFTYNXT50", "2024-10-25T14:30:00"));28
//        instruments.add(new Instrument("FINNIFTY", "2024-10-29T14:30:00"));29
//            instruments.add(new Instrument("MIDCPNIFTY", "2024-11-25T14:30:00"));30
//            instruments.add(new Instrument("MIDCPNIFTY", "2024-10-21T14:30:00"));31
//            instruments.add(new Instrument("NIFTY", "2028-06-29T14:30:00"));32
//            instruments.add(new Instrument("MIDCPNIFTY", "2024-10-28T14:30:00"));33
//            instruments.add(new Instrument("FINNIFTY", "2024-10-15T14:30:00"));34
//            instruments.add(new Instrument("NIFTY", "2024-11-28T14:30:00"));35
//            instruments.add(new Instrument("BANKNIFTY", "2024-10-30T14:30:00"));36
//            instruments.add(new Instrument("BANKNIFTY", "2024-12-24T14:30:00"));37
//            instruments.add(new Instrument("BANKNIFTY", "2024-12-24T14:30:00"));38
//            instruments.add(new Instrument("MIDCPNIFTY", "2024-12-30T14:30:00"));39
//            instruments.add(new Instrument("NIFTY", "2024-12-26T14:30:00"));40
//            instruments.add(new Instrument("FINNIFTY", "2024-10-29T14:30:00"));41
//            instruments.add(new Instrument("NIFTYNXT50", "2024-12-27T14:30:00"));42
//            instruments.add(new Instrument("FINNIFTY", "2024-11-26T14:30:00"));43
//            instruments.add(new Instrument("FINNIFTY", "2024-12-31T14:30:00"));44
//            instruments.add(new Instrument("BANKNIFTY", "2024-10-30T14:30:00"));45
//            instruments.add(new Instrument("MIDCPNIFTY", "2024-10-28T14:30:00"));46
//            instruments.add(new Instrument("NIFTYNXT50", "2024-11-29T14:30:00"));47
//            instruments.add(new Instrument("MIDCPNIFTY", "2024-10-31T14:30:00"));48
//            instruments.add(new Instrument("MIDCPNIFTY", "2024-10-28T14:30:00"));49
//            instruments.add(new Instrument("NIFTY", "2024-11-28T14:30:00"));50
//            instruments.add(new Instrument("BANKNIFTY", "2024-11-27T14:30:00"));51
//            instruments.add(new Instrument("MIDCPNIFTY", "2024-11-25T14:30:00"));52
//            instruments.add(new Instrument("NIFTYNXT50", "2024-10-25T14:30:00"));53
//            instruments.add(new Instrument("FINNIFTY", "2024-11-26T14:30:00"));54
//            instruments.add(new Instrument("NIFTY", "2024-10-31T14:30:00"));55
//            instruments.add(new Instrument("BANKNIFTY", "2024-10-30T14:30:00"));56
//            instruments.add(new Instrument("FINNIFTY", "2024-10-29T14:30:00"));57
//            instruments.add(new Instrument("NIFTYNXT50", "2024-10-25T14:30:00"));58
//            instruments.add(new Instrument("FINNIFTY", "2024-10-29T14:30:00"));59
//
//
//
//
//        for (Instrument instrument : instruments) {
//
//        String instrumentKey = instrument.getInstrumentType() + "_" + instrument.getContractExpiration();
//
//
//        dataStore.put(instrumentKey, instrument);
//    }
//
//
//        for (Map.Entry<String, Instrument> entry : dataStore.entrySet()) {
//        logger.info("Key: " + entry.getKey() + ", Value: " + entry.getValue());
//    }
//}
//}







