package ch.sthomas.stddivelogger.data.service;

import ch.sthomas.stddivelogger.data.repository.DiveRepository;
import ch.sthomas.stddivelogger.model.dive.Dive;
import ch.sthomas.stddivelogger.model.entity.DiveEntity;
import ch.sthomas.stddivelogger.model.user.User;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DiveDataService {
    final DiveRepository diveRepository;

    public DiveDataService(final DiveRepository diveRepository) {
        this.diveRepository = diveRepository;
    }

    public List<Dive> findDivesByUser(final User user) {
        return diveRepository.findByUser_Id(user.id()).stream().map(DiveEntity::toRecord).toList();
    }
}
