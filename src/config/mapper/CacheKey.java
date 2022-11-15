package config.mapper;

public class CacheKey {
    public String methodId;
    public Object params;

    public CacheKey(String methodId, Object params) {
        this.methodId = methodId;
        this.params = params;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj.getClass() != CacheKey.class)
            return false;

        CacheKey cacheKey = (CacheKey) obj;
        return this.methodId.equals(cacheKey.methodId) && this.params == cacheKey.params;
    }

    @Override
    public int hashCode() {
        int methodIdHashCode = methodId.hashCode();
        int paramsHashCode = params == null ? 0 : params.hashCode();

        return methodIdHashCode + paramsHashCode;
    }
}