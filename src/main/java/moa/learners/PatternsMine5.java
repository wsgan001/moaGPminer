package moa.learners;

import java.util.*;
import moa.MOAObject;
import moa.core.*;
import com.github.javacliparser.FloatOption;
import com.github.javacliparser.IntOption;
import com.yahoo.labs.samoa.instances.Prediction;
import com.yahoo.labs.samoa.instances.Instance;
import moa.core.InstanceExample;
import moa.clusterers.clustream.WithKmeans;
import moa.clusterers.clustream.Clustream;
import moa.cluster.Clustering;
import moa.cluster.Cluster;
import moa.cluster.SphereCluster;
import com.yahoo.labs.samoa.instances.SparseInstance;
import moa.utils.LCS;
import moa.utils.MapUtil;

/*
    This class defines new AbstractLearner that performs clustering of users to groups and 
    mining of global and group frequent itemsets.
    
*/
public class PatternsMine5 extends AbstractLearner implements Observer {

    private static final long serialVersionUID = 1L;
    
    private IncMine2 incMine;
    private WithKmeans clusterer = new WithKmeans();
    private Map<Integer,UserModel> usermodels = new HashMap<Integer, UserModel>();
    private int microclusteringUpdatesCounter = 0;
    
    public IntOption numMinNumberOfChangesInUserModel = new IntOption("minNumOfChangesInUserModel", 'c',
            "The minimal number of changes in user model to perform next actualization of clusters", 1, 1,
            Integer.MAX_VALUE);
    
    public IntOption numMinNumberOfMicroclustersUpdates = new IntOption("minNumOfMicroclustersUpdates", 'c',
            "The minimal number of microcluster updates to perform next kmeans of microclusters", 1, 1,
            Integer.MAX_VALUE);
   
    public IntOption numPages = new IntOption("numPages", 'p',
            "The number of pages in web page. Each number represents one page.", 1, 1,
            Integer.MAX_VALUE);
    
    public IntOption windowSizeOption = new IntOption(
            "windowSize", 'w',
            "Size of the sliding window (in number of segments).", 10);
    
    public IntOption maxItemsetLengthOption = new IntOption(
            "maxItemsetLength", 'm',
            "Maximum length of frequent closed itemset to be considered.", -1);
    
    public IntOption numberOfGroupsOption = new IntOption(
            "numberOfGroups", 'g',
            "Number of groups to be created from users.", 2);
    
    public FloatOption minSupportOption = new FloatOption(
            "minSupport", 's',
            "Minimum support.", 0.1, 0, 1);
    
    public FloatOption relaxationRateOption = new FloatOption(
            "relaxationRate", 'r',
            "Relaxation Rate.", 0.5, 0, 1);
    
    public IntOption fixedSegmentLengthOption = new IntOption(
            "fixedSegmentLength", 'l',
            "Fixed Segment Length.", 1000);
    
    public IntOption numberOfRecommendedItemsOption = new IntOption(
            "numberOfRecommendedItems", 'n',
            "Number of item recommended.", 5);
    
    public IntOption evaluationWindowSizeOption = new IntOption(
            "evaluationWindowSize", 'e',
            "Size of evaluation window.", 3);
    
    private Clustering kmeansClustering;
     
    public PatternsMine5(){
        super();
        this.clusterer = new WithKmeans();
        this.clusterer.kOption.setValue(numberOfGroupsOption.getValue());
        this.clusterer.maxNumKernelsOption.setValue(100);
        this.clusterer.kernelRadiFactorOption.setValue(2);
        
    }
    
    @Override
    public void resetLearningImpl() {
        System.out.println("restart learning");
        GroupCounter.groupscounters = new int [numberOfGroupsOption.getValue() + 1];
        for(int i = 0; i < GroupCounter.groupscounters.length; i++){
            GroupCounter.groupscounters[i] = 0;
        }
        this.incMine = new IncMine2( windowSizeOption.getValue(), maxItemsetLengthOption.getValue(),
                numberOfGroupsOption.getValue(), minSupportOption.getValue(),
                relaxationRateOption.getValue(),fixedSegmentLengthOption.getValue());
        this.incMine.resetLearning();
        this.clusterer =  new WithKmeans();
        this.clusterer.kOption.setValue(numberOfGroupsOption.getValue());
        this.clusterer.maxNumKernelsOption.setValue(100);
        this.clusterer.kernelRadiFactorOption.setValue(2);
        this.clusterer.resetLearning();
    }
    
    @Override
    public void trainOnInstance(Example e) {
        // first update user model with new data
        Instance inst = (Instance) e.getData();
//        UserModel um = updateUserModel(inst);
//        if(um.getNumberOfChanges() > this.numMinNumberOfChangesInUserModel.getValue()){
//            um.setNumberOfChanges(0);
//            // perform clustering with user model 
//            Instance umInstance = um.toInstance(numPages.getValue());
//            clusterer.trainOnInstance(umInstance);
//            if(this.microclusteringUpdatesCounter++ > this.numMinNumberOfMicroclustersUpdates.getValue()){
//                this.microclusteringUpdatesCounter = 0;
//                Clustering results = clusterer.getMicroClusteringResult(); // append group to instance that it belongs to...
//                if(results != null){
//                    AutoExpandVector<Cluster> clusters = results.getClustering();
//                    if(clusters.size() > 0){
//                        // save new clustering
//                        this.kmeansClustering = Clustream.kMeans(
//                                this.numberOfGroupsOption.getValue(),
//                                clusters);
//                    }
//                }
//            }
//        }
       
        int nItems = inst.numValues();
        double[] attValues = new double[nItems];
        int[] indices = new int[nItems];
        attValues[0] = -1; 
        indices[0]   = 0;
        for(int idx = 1; idx < nItems; idx++){
            attValues[idx] = inst.value(idx);
            indices[idx] =  inst.index(idx);
        }
        Instance instanceWithoutGroupid = new SparseInstance(1.0,attValues,indices,nItems);
        InstanceExample instEx = new InstanceExample(instanceWithoutGroupid);
        //incMine.trainOnInstance(e); // first train on instance with groupid - group

        incMine.trainOnInstance(instEx);   // then train on instance without groupid - global
        
        
    }

    @Override
    public double[] getVotesForInstance(Example e) {
        // append group to instance that it belongs to...
        Instance session = (Instance)e.getData();
        
        // we need to copy instance data to sessionArray where we can modify them 
        // because data in Instance cannot be changed i dont know why...
        ArrayList<Double> sessionArray = new ArrayList<>(); 
        for(int i = 0; i < session.numValues(); i++){
            sessionArray.add(i,session.value(i)); 
        }
        
        int evaluationWindowSize = evaluationWindowSizeOption.getValue();
        int numberOfRecommendedItems = numberOfRecommendedItemsOption.getValue();
        // TESTING Instance - how it performs on recommendation.
        // get window from actual instance
        List<Integer> window = new ArrayList<>(); // items inside window 
        List<Integer> outOfWindow = new ArrayList<>(); // items out of window 
        if(evaluationWindowSize >= (sessionArray.size()-1)){ 
            return null; // this is when window is too small - it is ignored.
        }
        for(int i = 2; i <= evaluationWindowSize + 1; i++){ // first item is groupid, 2nd uid
            window.add((int) Math.round(sessionArray.get(i)));
        }
        // maximum number of evaluated future items is the same as number of recommended items.
        for(int i = evaluationWindowSize + 2, j = 0; 
            i < sessionArray.size() && j < numberOfRecommendedItems;
            i++, j++){
            outOfWindow.add((int) Math.round(sessionArray.get(i)));
        }
        List<Integer> recommendations = new ArrayList<>();
        // how to get all fcis found ?
        FCITable fciGlobal = this.incMine.fciTableGlobal;
        Iterator<SemiFCI> it = fciGlobal.iterator();
        Map<SemiFCI, FciValue> mapFciWeight = new HashMap<>();
        
        while(it.hasNext()){
            SemiFCI fci = it.next();
            if(fci.size() > 1){
                List<Integer> items = fci.getItems();
                double lcsVal = ((double)LCS.computeLongestCommonSubset(items,window)) / ((double)window.size());
                if(lcsVal < 0.1){
                    continue;
                }
                double support = 
                       ((double)fci.getApproximateSupport())/((double)this.fixedSegmentLengthOption.getValue());
                if(support < this.minSupportOption.getValue()){
                     continue;
                }
                FciValue fciVal = new FciValue();
                fciVal.computeValue(lcsVal, support);
                mapFciWeight.put(fci, fciVal);
            }
        }
//         This next block performs the same with group fcis. 
//         This can be commented out to test performance without group fcis.
        if(sessionArray.get(0) > -1){  // if group has some fci append it to list
            List<FCITable> fciTables = this.incMine.fciTablesGroups;
            FCITable fciGroup = fciTables.get((int) Math.round(sessionArray.get(0)));
            Iterator<SemiFCI> itG = fciGroup.iterator();
            while(itG.hasNext()){
                SemiFCI fci = itG.next();
                if(fci.size() > 1){
                    List<Integer> items = fci.getItems();
                    double lcsVal = LCS.computeLongestCommonSubset(items,window)/((double)window.size());
                    if(lcsVal < 0.1){
                            continue;
                      }
                     double support = (double)fci.getApproximateSupport()/(double)this.fixedSegmentLengthOption.getValue();
                    if(support < this.minSupportOption.getValue()){
                        continue;
                    }
//                    int countUsers = GroupCounter.getCountOfAllUsers();
//                    int countGroup = GroupCounter.groupscounters[sessionArray.get(0).intValue()];
//                    double ratio = (double)countGroup/((double)countUsers/(GroupCounter.groupscounters.length-1));
                    
                    FciValue fciVal = new FciValue();
                    fciVal.computeValue(lcsVal, support);
                    mapFciWeight.put(fci, fciVal);
                }
            }
        }
        
        // another solution every time take best 2 patterns from group and from global
        // prefer items that are both in global and group pattern.
        
        // all fcis found have to be sorted descending by its support and similarity.
        Map<SemiFCI, FciValue> sortedByValue = MapUtil.sortByValue(mapFciWeight);
        List<Integer> recs = new ArrayList<>(); 
        for(Map.Entry<SemiFCI, FciValue> entry : sortedByValue.entrySet()) {
            SemiFCI key = entry.getKey();
            List<Integer> items = key.getItems();
            Iterator<Integer> itItems = items.iterator();
            while(itItems.hasNext()){
                Integer item =  itItems.next();
                if(!window.contains(item) && !recs.contains(item)){  // create unique recommendations
                    recs.add(item);
                    if(recs.size() >= numberOfRecommendedItems){
                        break;
                    }
                }
            }
            if(recs.size() >= numberOfRecommendedItems){
                 break;
            }
        }
        
        double lcsVal = LCS.computeLongestCommonSubset(outOfWindow, recs);
        // lcsVal contains number of items that are same in outOfWindow and recs
        double[] lcsValues = new double[1];
        lcsValues[0] = lcsVal; // in future the window will slide over actual session. 
                               // Now only one set of recommended items is created
        return lcsValues;
        
    }
    
    @Override
    public void trainOnInstanceImpl(Instance inst) {
        System.out.println("trainOnInstanceImpl");
    }

    @Override
    protected Measurement[] getModelMeasurementsImpl() {
        System.out.println("getModelMeasurementsImpl");
        return null;
    }

    @Override
    public void getModelDescription(StringBuilder out, int indent) {
        System.out.println("");
    }

    @Override
    public boolean isRandomizable() {
         return false;
    }

   

    @Override
    public Learner[] getSublearners() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public MOAObject getModel() {
         return null;
    }

    @Override
    public Prediction getPredictionForInstance(Example e) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void update(Observable o, Object arg) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    
    private UserModel updateUserModel(Instance inst){
//        System.out.println("updating user model");
//        int uid = (int)inst.value(1);
//        if(usermodels.containsKey(uid)){
//            UserModel um = usermodels.get(uid);
//            Map<Integer,Integer> pageVisitsMap = um.getPageVisitsMap();
//            for(int i = 2; i < inst.numValues(); i++){ // from i = 2 because first val is groupid and second uid
//                int idx = (int)inst.value(i);
//                if(pageVisitsMap.containsKey(idx)){
//                    int actVal = pageVisitsMap.get(idx);
//                    um.put(idx,actVal + 1);
//                }else{
//                    um.put(idx, 1);
//                }
//            }
//             System.out.println("user model updated");
//            return um;
//        }else{
//            UserModel um = new UserModel();
//            um.setGroupid(inst.value(0));
//            um.setId((int)inst.value(1));
//            Map<Integer,Integer> pageVisitsMap = um.getPageVisitsMap();
//            for(int i = 2; i < inst.numValues(); i++){ // from i = 2 because first val is groupid and second uid
//                int idx = (int)inst.value(i);
//                if(pageVisitsMap.containsKey(idx)){
//                    int actVal = pageVisitsMap.get(idx);
//                    um.put(idx,actVal + 1);
//                }else{
//                    um.put(idx, 1);
//                }
//            }
//            usermodels.put(uid, um);
//            return um;
//        }
        return null;
       
    }
    
//    private Instance getUserModelInstanceFromExampleInstance(Instance inst){
//        int uid = (int)inst.value(1);
//        if(usermodels.containsKey(uid)){
//            UserModel um = usermodels.get(uid);
//            return um.toInstance(numPages.getValue());
//        }else{
//            return null;
//        }
//    }

    private UserModel getUserModelFromInstance(Instance inst) {
        return null;
    }
   

}
