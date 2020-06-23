package barley.appmgt.usage.client.impl;

public class QueryServiceStub {

    public static class CompositeIndex {
        private String indexName;
        private String rangeFirst;
        private String rangeLast;

        public String getIndexName() {
            return indexName;
        }

        public void setIndexName(String indexName) {
            this.indexName = indexName;
        }

        public String getRangeFirst() {
            return rangeFirst;
        }

        public void setRangeFirst(String rangeFirst) {
            this.rangeFirst = rangeFirst;
        }

        public String getRangeLast() {
            return rangeLast;
        }

        public void setRangeLast(String rangeLast) {
            this.rangeLast = rangeLast;
        }
    }
}
