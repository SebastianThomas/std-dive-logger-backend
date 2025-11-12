package ch.sthomas.stddivelogger.model.controller.dive;

import ch.sthomas.stddivelogger.model.dive.DiveSite;

public record UploadDiveBody(int userId, DiveSite site) {}
