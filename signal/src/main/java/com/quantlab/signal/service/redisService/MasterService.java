package com.quantlab.signal.service.redisService;

import com.quantlab.signal.dto.redisDto.MasterResponseFO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class MasterService {

        @Autowired
        private  MasterRepository repository;

        public  void saveResponseList(String key, List<MasterResponseFO> responses) {
//            repository.saveList(key, responses);
            processMasterResponses(responses);
        }


        public  List<MasterResponseFO> getResponseList(String key) {
            return repository.findAll(key);
        }

        public  void clearResponseList(String key) {
            repository.clearList(key);
        }



    private final Map<String, List<MasterResponseFO>> masterResponseMap = new HashMap<>();
    private final MasterRepository masterRepository;

    @Autowired
    public MasterService(MasterRepository masterRepository) {
        this.masterRepository = masterRepository;
    }


    public void processMasterResponses(List<MasterResponseFO> response) {

        for (MasterResponseFO masterResponse : response) {

            String key = masterResponse.getInstrumentType() + "_" + masterResponse.getContractExpiration();

            masterResponse.setInstrumentKey(key);

            if (masterResponseMap.containsKey(key)){
                List<MasterResponseFO> newdata = masterResponseMap.get(key);
                newdata.add(masterResponse);
                masterResponseMap.put(key,newdata);

            }else {
                List<MasterResponseFO> newdata = new ArrayList<>();
                newdata.add(masterResponse);
                masterResponseMap.put(key,newdata);
            }
        }
        masterResponseMap.forEach(masterRepository::saveList);
    }
    public List<MasterResponseFO> getMasterResponsesByKey(String key) {

        return masterResponseMap.getOrDefault(key, new ArrayList<>());
    }





}


