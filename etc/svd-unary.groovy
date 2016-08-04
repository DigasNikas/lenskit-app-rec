import org.grouplens.lenskit.transform.normalize.UnitVectorNormalizer
import org.grouplens.lenskit.transform.normalize.UserVectorNormalizer
import org.grouplens.lenskit.transform.normalize.VectorNormalizer
import org.lenskit.api.ItemScorer
import org.lenskit.baseline.BaselineScorer
import org.lenskit.basic.ConstantItemScorer
import org.lenskit.basic.ConstantItemScorer.Value
import org.lenskit.mf.funksvd.FunkSVDItemScorer
import org.lenskit.mf.funksvd.FeatureCount
import org.grouplens.lenskit.iterative.IterationCount

bind ItemScorer to FunkSVDItemScorer
bind (BaselineScorer, ItemScorer) to ConstantItemScorer

within (UserVectorNormalizer) {
    bind VectorNormalizer to UnitVectorNormalizer
}

set FeatureCount to 15
set IterationCount to 100
set ConstantItemScorer.Value to 0

