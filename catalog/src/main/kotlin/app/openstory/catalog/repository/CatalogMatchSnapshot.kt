package app.openstory.catalog.repository

import app.openstory.catalog.matching.CatalogMatchCandidate

data class CatalogMatchSnapshot(val candidates: List<CatalogMatchCandidate>)
