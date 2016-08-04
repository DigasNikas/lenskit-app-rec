import org.grouplens.lenskit.transform.normalize.MeanCenteringVectorNormalizer
import org.grouplens.lenskit.transform.normalize.UserVectorNormalizer
import org.grouplens.lenskit.transform.normalize.VectorNormalizer
import org.lenskit.api.ItemScorer
import org.lenskit.baseline.BaselineScorer
import org.lenskit.baseline.ItemMeanRatingItemScorer
import org.lenskit.baseline.UserMeanBaseline
import org.lenskit.baseline.UserMeanItemScorer
import org.lenskit.mf.funksvd.FunkSVDItemScorer
import org.lenskit.mf.funksvd.FeatureCount
import org.grouplens.lenskit.iterative.IterationCount

bind ItemScorer to FunkSVDItemScorer
bind (BaselineScorer, ItemScorer) to UserMeanItemScorer
bind (UserMeanBaseline, ItemScorer) to ItemMeanRatingItemScorer
set FeatureCount to 25
set IterationCount to 125


