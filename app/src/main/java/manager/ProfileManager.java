package manager;

import com.example.coreclash.data.LocalProfileRepository;
import com.example.coreclash.data.ProfileRepository;
import com.example.coreclash.model.PlayerProfile;

import util.NullUtil;

public class ProfileManager {

    private PlayerProfile currentProfile;
    public ProfileRepository profileRepository;
    public LocalProfileRepository localProfileRepository;

    public void setCurrentProfile(PlayerProfile profile) {
        this.currentProfile = profile;
    }
    public void persistProfile() {
        if (NullUtil.isNull(currentProfile)) {
            return;
        }
        localProfileRepository.saveProfile(currentProfile);
        if (!NullUtil.isNull(profileRepository)) {
            profileRepository.saveProfile(currentProfile);
        }
    }
}
