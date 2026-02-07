package manager;

import com.example.coreclash.data.LocalProfileRepository;
import com.example.coreclash.data.ProfileRepository;
import com.example.coreclash.model.PlayerProfile;

public class ProfileManager {

    private PlayerProfile currentProfile;
    public ProfileRepository profileRepository;
    public LocalProfileRepository localProfileRepository;

    public void setCurrentProfile(PlayerProfile profile) {
        this.currentProfile = profile;
    }
    public void persistProfile() {
        if (currentProfile == null) return;
        localProfileRepository.saveProfile(currentProfile);
        if (profileRepository != null) {
            profileRepository.saveProfile(currentProfile);
        }
    }
}
