package in.wynk.payment.dto.aps.common;

public class Secret {
    private String secretKey;
    private String salt;

    public Secret() {
    }

    public String getSecretKey() {
        return this.secretKey;
    }

    public String getSalt() {
        return this.salt;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public void setSalt(String salt) {
        this.salt = salt;
    }

    public boolean equals(Object o) {
        if (o == this) {
            return true;
        } else if (!(o instanceof Secret)) {
            return false;
        } else {
            Secret other = (Secret) o;
            Object this$secretKey = this.getSecretKey();
            Object other$secretKey = other.getSecretKey();
            if (this$secretKey == null) {
                if (other$secretKey != null) {
                    return false;
                }
            } else if (!this$secretKey.equals(other$secretKey)) {
                return false;
            }

            Object this$salt = this.getSalt();
            Object other$salt = other.getSalt();
            if (this$salt == null) {
                if (other$salt != null) {
                    return false;
                }
            } else if (!this$salt.equals(other$salt)) {
                return false;
            }

            return true;
        }
    }

    public int hashCode() {
        int result = 1;
        Object $secretKey = this.getSecretKey();
        result = result * 59 + ($secretKey == null ? 43 : $secretKey.hashCode());
        Object $salt = this.getSalt();
        result = result * 59 + ($salt == null ? 43 : $salt.hashCode());
        return result;
    }
}
